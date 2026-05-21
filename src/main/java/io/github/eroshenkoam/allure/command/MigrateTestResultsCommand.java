package io.github.eroshenkoam.allure.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eroshenkoam.allure.client.ServiceBuilder;
import io.github.eroshenkoam.allure.client.TestResultService;
import io.github.eroshenkoam.allure.client.dto.Page;
import io.github.eroshenkoam.allure.client.dto.TestResult;
import io.github.eroshenkoam.allure.client.dto.TestResultAttachment;
import io.github.eroshenkoam.allure.client.dto.TestResultScenario;
import io.github.eroshenkoam.allure.client.dto.TestResultStep;
import io.github.eroshenkoam.allure.client.dto.scenario.AttachmentStep;
import io.github.eroshenkoam.allure.client.dto.scenario.BodyStep;
import io.github.eroshenkoam.allure.client.dto.scenario.ExpectedBodyStep;
import io.github.eroshenkoam.allure.client.dto.scenario.ScenarioStep;
import io.github.eroshenkoam.allure.client.dto.scenario.TestResultScenarioV2;
import io.github.eroshenkoam.allure.textmarkup.MarkdownToJsonConverter;
import lombok.Data;
import lombok.experimental.Accessors;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

/**
 * Rewrites legacy (v1) test result scenarios as formatted v2 scenarios so that
 * historical launches stay readable for stakeholders.
 *
 * Relies on the hidden migration endpoint exposed by TestResultScenarioMigrationController:
 *   POST /api/rs/testresult/{id}/scenario?v2
 */
@CommandLine.Command(
        name = "migrate-testresults",
        mixinStandardHelpOptions = true,
        description = "Migrate test result scenarios from legacy plain-text shape to formatted v2 JSON"
)
public class MigrateTestResultsCommand extends AbstractTestOpsCommand {

    private static final Integer PAGE_SIZE = 200;

    private static final ObjectMapper MAPPER = new ObjectMapper().disable(FAIL_ON_UNKNOWN_PROPERTIES);

    private static final Pattern HEADER_PATTERN =
            Pattern.compile("#+ (?<text>.+)");
    private static final Pattern LIST_ITEM_PATTERN =
            Pattern.compile("(?<spaces>[ \\t]*)([*\\-]) (?<text>.+)");
    private static final Pattern TABLE_PATTERN =
            Pattern.compile("^[ \\t]*\\|(.*\\|)+$");

    private static final Pattern INLINE_ATTACHMENT_PATTERN =
            Pattern.compile(
                    "!\\[(?<alt>[^\\]]*)]\\(/api/rs/(?<entity>testcase|testresult)/attachment/(?<id>\\d+)/content\\)");
    private static final Pattern WHOLE_ATTACHMENT_PATTERN =
            Pattern.compile(
                    "^!\\[(?<alt>[^\\]]*)]\\(/api/rs/(?<entity>testcase|testresult)/attachment/(?<id>\\d+)/content\\)$");

    private static final Pattern EXPECTED_PATTERN =
            Pattern.compile("\\{\"action\":\"(?<action>.*)\",\"expected\":\"(?<expected>.*)(\"})+");

    @CommandLine.Option(
            names = {"--allure.project.id"},
            description = "Allure TestOps project id",
            defaultValue = "${env:ALLURE_PROJECT_ID}",
            required = true
    )
    protected Long allureProjectId;

    @CommandLine.Option(
            names = {"--allure.testresult.filter"},
            description = "Test result RQL filter (e.g. 'launch.closed=true and createdDate < 1700000000000')",
            defaultValue = "${env:ALLURE_TESTRESULT_FILTER}",
            required = true
    )
    protected String allureTestResultFilter;

    @CommandLine.Option(
            names = {"--dry-run"},
            description = "Print converted v2 scenarios instead of writing them back",
            defaultValue = "false"
    )
    protected boolean dryRun;

    @Override
    public void runUnsafe(final ServiceBuilder builder) throws Exception {
        final TestResultService trService = builder.create(TestResultService.class);

        final Set<Long> ids = collectTestResultIds(trService);
        System.out.printf("Migrating %d test results from project %d%n", ids.size(), allureProjectId);

        invokeParallel(
                String.format("[%d] migrate test result scenarios", allureProjectId),
                ids,
                (id) -> migrateOne(trService, id)
        );
    }

    private void migrateOne(final TestResultService trService, final Long testResultId) throws Exception {
        final TestResultScenario legacy = executeRequest(trService.getScenario(testResultId));
        if (legacy == null
                || (isEmpty(legacy.getSteps()) && isEmpty(legacy.getAttachments()))) {
            System.out.printf("Skip test result %d: no scenario%n", testResultId);
            return;
        }

        final AttachmentContext context = createAttachmentContext(trService, testResultId);
        final TestResultScenarioV2 converted = convert(legacy, context);

        if (converted.getSteps().isEmpty()) {
            System.out.printf("Skip test result %d: conversion produced empty scenario%n", testResultId);
            return;
        }

        if (dryRun) {
            System.out.printf("[dry-run] test result %d -> %s%n",
                    testResultId,
                    MAPPER.writeValueAsString(converted));
            return;
        }

        executeRequest(trService.setScenario(testResultId, converted));
        System.out.printf("Migrated test result %d (%d top-level steps)%n",
                testResultId, converted.getSteps().size());
    }

    private static TestResultScenarioV2 convert(final TestResultScenario legacy, final AttachmentContext context) {
        final List<ScenarioStep> steps = new ArrayList<>();

        if (legacy.getSteps() != null) {
            for (final TestResultStep step : legacy.getSteps()) {
                convertStep(step, context).ifPresent(steps::add);
            }
        }
        if (legacy.getAttachments() != null) {
            for (final TestResultAttachment att : legacy.getAttachments()) {
                if (att != null && att.getId() != null) {
                    steps.add(new AttachmentStep().setAttachmentId(att.getId()));
                }
            }
        }
        return new TestResultScenarioV2().setSteps(steps);
    }

    private static Optional<ScenarioStep> convertStep(final TestResultStep step, final AttachmentContext context) {
        if (step == null) {
            return Optional.empty();
        }

        if ("expected".equalsIgnoreCase(step.getKeyword())) {
            final Optional<ScenarioStep> actionExpected = convertActionExpectedStep(step, context);
            if (actionExpected.isPresent()) {
                return actionExpected;
            }
        }

        final String rawBody = composeBody(step.getKeyword(), step.getName());
        final List<ScenarioStep> bodyChildrenFromText = new ArrayList<>();
        final BodyStep body = new BodyStep();

        if (StringUtils.isNotBlank(rawBody)) {
            final List<ScenarioStep> textParts = parseTextToSteps(rawBody, false, context);
            if (!textParts.isEmpty() && textParts.getFirst() instanceof BodyStep first) {
                body.setBody(first.getBody()).setBodyJson(first.getBodyJson());
                bodyChildrenFromText.addAll(textParts.subList(1, textParts.size()));
            } else {
                body.setBody(rawBody).setBodyJson(MarkdownToJsonConverter.convertToJson(rawBody));
                bodyChildrenFromText.addAll(textParts);
            }
        } else {
            body.setBody("").setBodyJson(MarkdownToJsonConverter.convertToJson(""));
        }

        final List<ScenarioStep> children = new ArrayList<>(bodyChildrenFromText);
        if (step.getSteps() != null) {
            for (final TestResultStep sub : step.getSteps()) {
                convertStep(sub, context).ifPresent(children::add);
            }
        }
        if (step.getAttachments() != null) {
            for (final TestResultAttachment att : step.getAttachments()) {
                if (att != null && att.getId() != null) {
                    children.add(new AttachmentStep().setAttachmentId(att.getId()));
                }
            }
        }
        if (!children.isEmpty()) {
            body.setSteps(children);
        }

        if (StringUtils.isNotBlank(step.getExpectedResult())) {
            final List<ScenarioStep> expected = parseTextToSteps(step.getExpectedResult(), true, context);
            if (!expected.isEmpty()) {
                body.setExpectedResultSteps(expected);
            }
        }

        return Optional.of(body);
    }

    private static Optional<ScenarioStep> convertActionExpectedStep(final TestResultStep step,
                                                                    final AttachmentContext context) {
        final Optional<StepExpected> parsed = readStepExpected(StringUtils.trimToNull(step.getName()));
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        final StepExpected payload = parsed.get();
        if (StringUtils.isBlank(payload.getAction()) && StringUtils.isBlank(payload.getExpected())) {
            return Optional.empty();
        }

        final List<ScenarioStep> actionSteps = parseTextToSteps(
                StringUtils.defaultIfBlank(payload.getAction(), "Action"), false, context);
        final ScenarioStep first = actionSteps.isEmpty() ? null : actionSteps.getFirst();

        final BodyStep body;
        final List<ScenarioStep> children = new ArrayList<>();
        if (first instanceof BodyStep firstBody) {
            body = firstBody;
            children.addAll(actionSteps.subList(1, actionSteps.size()));
        } else {
            body = new BodyStep()
                    .setBody("Action")
                    .setBodyJson(MarkdownToJsonConverter.convertToJson("Action"));
            children.addAll(actionSteps);
        }

        if (step.getSteps() != null) {
            for (final TestResultStep sub : step.getSteps()) {
                convertStep(sub, context).ifPresent(children::add);
            }
        }
        if (step.getAttachments() != null) {
            for (final TestResultAttachment att : step.getAttachments()) {
                if (att != null && att.getId() != null) {
                    children.add(new AttachmentStep().setAttachmentId(att.getId()));
                }
            }
        }
        if (!children.isEmpty()) {
            body.setSteps(children);
        }

        if (StringUtils.isNotBlank(payload.getExpected())) {
            final List<ScenarioStep> expected = parseTextToSteps(payload.getExpected(), true, context);
            if (!expected.isEmpty()) {
                body.setExpectedResultSteps(expected);
            }
        }
        return Optional.of(body);
    }

    private static Optional<StepExpected> readStepExpected(final String content) {
        if (content == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(MAPPER.readValue(content, StepExpected.class));
        } catch (IOException e) {
            final Matcher matcher = EXPECTED_PATTERN.matcher(content);
            if (matcher.matches()) {
                return Optional.of(new StepExpected()
                        .setAction(matcher.group("action"))
                        .setExpected(matcher.group("expected")));
            }
            return Optional.empty();
        }
    }

    private static List<ScenarioStep> parseTextToSteps(final String text,
                                                       final boolean expected,
                                                       final AttachmentContext context) {
        final List<ScenarioStep> result = new ArrayList<>();
        final List<ParsedLine> lines = parseLines(text);
        for (final ParsedLine line : lines) {
            switch (line.getType()) {
                case ATTACHMENT -> resolveAttachmentId(context, line.getEntity(), Long.parseLong(line.getContent()))
                        .map(id -> new AttachmentStep().setAttachmentId(id))
                        .ifPresent(result::add);
                case CONTENT -> {
                    if (expected) {
                        result.add(new ExpectedBodyStep()
                                .setBody(line.getContent())
                                .setBodyJson(MarkdownToJsonConverter.convertToJson(line.getContent())));
                    } else {
                        result.add(new BodyStep()
                                .setBody(line.getContent())
                                .setBodyJson(MarkdownToJsonConverter.convertToJson(line.getContent())));
                    }
                }
                default -> { /* ignore */ }
            }
        }
        return result;
    }

    private static List<ParsedLine> parseLines(final String text) {
        final String safe = Optional.ofNullable(text).orElse("");
        final List<ParsedLine> raw = Arrays.stream(safe.split("\n"))
                .filter(s -> !s.isBlank())
                .map(StringEscapeUtils::unescapeJson)
                .flatMap(line -> splitInlineAttachments(line).stream())
                .toList();
        return collect(raw);
    }

    private static List<ParsedLine> splitInlineAttachments(final String line) {
        final Matcher whole = WHOLE_ATTACHMENT_PATTERN.matcher(line);
        if (whole.matches()) {
            return List.of(new ParsedLine()
                    .setType(LineType.ATTACHMENT)
                    .setEntity(whole.group("entity"))
                    .setContent(whole.group("id")));
        }
        final Matcher inline = INLINE_ATTACHMENT_PATTERN.matcher(line);
        if (!inline.find()) {
            return List.of(reshape(line));
        }
        final List<ParsedLine> parsed = new ArrayList<>();
        int start = 0;
        do {
            final String before = line.substring(start, inline.start());
            if (!before.isBlank()) {
                parsed.add(reshape(before));
            }
            parsed.add(new ParsedLine()
                    .setType(LineType.ATTACHMENT)
                    .setEntity(inline.group("entity"))
                    .setContent(inline.group("id")));
            start = inline.end();
        } while (inline.find());
        final String after = line.substring(start);
        if (!after.isBlank()) {
            parsed.add(reshape(after));
        }
        return parsed;
    }

    private static ParsedLine reshape(final String line) {
        final Matcher header = HEADER_PATTERN.matcher(line);
        if (header.matches()) {
            return new ParsedLine()
                    .setType(LineType.CONTENT)
                    .setContent(String.format("**%s**", header.group("text")));
        }
        final Matcher listItem = LIST_ITEM_PATTERN.matcher(line);
        if (listItem.matches()) {
            return new ParsedLine()
                    .setType(LineType.CONTENT)
                    .setContent(String.format("%s- %s", listItem.group("spaces"), listItem.group("text")));
        }
        if (TABLE_PATTERN.matcher(line).matches()) {
            // tables in test result text are uncommon; we drop the rendering hint and keep the raw line
            return new ParsedLine()
                    .setType(LineType.CONTENT)
                    .setContent(line);
        }
        return new ParsedLine()
                .setType(LineType.CONTENT)
                .setContent(line);
    }

    private static List<ParsedLine> collect(final List<ParsedLine> lines) {
        final List<ParsedLine> result = new ArrayList<>();
        if (lines.isEmpty()) {
            return result;
        }
        result.add(lines.getFirst());
        for (int i = 1; i < lines.size(); i++) {
            final ParsedLine current = lines.get(i);
            final ParsedLine prev = result.getLast();
            if (current.getType() == LineType.CONTENT && prev.getType() == LineType.CONTENT) {
                prev.setContent(prev.getContent() + "\n" + current.getContent());
            } else {
                result.add(current);
            }
        }
        return result;
    }

    private static String composeBody(final String keyword, final String name) {
        final String safeName = StringUtils.isBlank(name) ? "" : name;
        if (StringUtils.isBlank(keyword)) {
            return safeName;
        }
        return keyword + " " + safeName;
    }

    private static AttachmentContext createAttachmentContext(final TestResultService service,
                                                             final Long testResultId) throws IOException {
        final Set<Long> ownedAttachmentIds = ConcurrentHashMap.newKeySet();
        Page<TestResultAttachment> current = new Page<TestResultAttachment>().setNumber(-1);
        do {
            current = executeRequest(service.getAttachments(testResultId, current.getNumber() + 1, PAGE_SIZE));
            if (current == null || current.getContent() == null) {
                break;
            }
            for (final TestResultAttachment attachment : current.getContent()) {
                if (attachment.getId() != null) {
                    ownedAttachmentIds.add(attachment.getId());
                }
            }
        } while (current.getNumber() + 1 < current.getTotalPages());
        return new AttachmentContext()
                .setService(service)
                .setTestResultId(testResultId)
                .setOwnedAttachmentIds(ownedAttachmentIds)
                .setRemappedAttachmentIds(new ConcurrentHashMap<>());
    }

    private static Optional<Long> resolveAttachmentId(final AttachmentContext context,
                                                      final String sourceEntity,
                                                      final Long sourceAttachmentId) {
        if ("testresult".equals(sourceEntity) && context.getOwnedAttachmentIds().contains(sourceAttachmentId)) {
            return Optional.of(sourceAttachmentId);
        }
        final Long cached = context.getRemappedAttachmentIds().get(sourceAttachmentId);
        if (cached != null) {
            return Optional.of(cached);
        }
        try (ResponseBody body = executeRequest("testcase".equals(sourceEntity)
                ? context.getService().getTestCaseAttachmentContent(sourceAttachmentId)
                : context.getService().getAttachmentContent(sourceAttachmentId))) {
            if (body == null || body.contentLength() == 0) {
                System.out.printf("Attachment %s/%d has no content, dropping step%n",
                        sourceEntity, sourceAttachmentId);
                return Optional.empty();
            }
            final MediaType mediaType = Objects.requireNonNullElseGet(
                    body.contentType(), () -> MediaType.parse("application/octet-stream"));
            final byte[] content = body.bytes();
            final RequestBody requestBody = RequestBody.create(content, mediaType);
            final String fileName = String.format("migrated-%s-%d", sourceEntity, sourceAttachmentId);
            final MultipartBody.Part part = MultipartBody.Part.createFormData("file", fileName, requestBody);

            final List<TestResultAttachment> created = executeRequest(
                    context.getService().createAttachment(context.getTestResultId(), List.of(part)));
            if (created == null || created.isEmpty()) {
                return Optional.empty();
            }
            final Long newId = created.getFirst().getId();
            context.getRemappedAttachmentIds().put(sourceAttachmentId, newId);
            context.getOwnedAttachmentIds().add(newId);
            System.out.printf("Copied attachment %s/%d to test result %d as %d%n",
                    sourceEntity, sourceAttachmentId, context.getTestResultId(), newId);
            return Optional.of(newId);
        } catch (Exception e) {
            System.out.printf("Failed to copy attachment %s/%d into test result %d: %s%n",
                    sourceEntity, sourceAttachmentId, context.getTestResultId(), e.getMessage());
            return Optional.empty();
        }
    }

    private Set<Long> collectTestResultIds(final TestResultService service) throws IOException {
        final Set<Long> ids = new HashSet<>();
        Page<TestResult> current = new Page<TestResult>().setNumber(-1);
        do {
            current = executeRequest(service.findByRql(
                    allureProjectId, allureTestResultFilter, current.getNumber() + 1, PAGE_SIZE));
            if (current == null || current.getContent() == null) {
                break;
            }
            for (final TestResult tr : current.getContent()) {
                ids.add(tr.getId());
            }
        } while (current.getNumber() + 1 < current.getTotalPages());
        return ids;
    }

    private static boolean isEmpty(final List<?> list) {
        return list == null || list.isEmpty();
    }

    private enum LineType { CONTENT, ATTACHMENT }

    @Data
    @Accessors(chain = true)
    private static class ParsedLine {
        private LineType type;
        private String content;
        private String entity;
    }

    @Data
    @Accessors(chain = true)
    private static class StepExpected {
        private String action;
        private String expected;
    }

    @Data
    @Accessors(chain = true)
    private static class AttachmentContext {
        private TestResultService service;
        private Long testResultId;
        private Set<Long> ownedAttachmentIds;
        private Map<Long, Long> remappedAttachmentIds;
    }
}
