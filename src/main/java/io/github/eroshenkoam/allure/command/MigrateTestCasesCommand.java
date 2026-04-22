package io.github.eroshenkoam.allure.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eroshenkoam.allure.textmarkup.MarkdownToJsonConverter;
import io.github.eroshenkoam.allure.client.ProjectService;
import io.github.eroshenkoam.allure.client.ServiceBuilder;
import io.github.eroshenkoam.allure.client.TestCaseScenarioService;
import io.github.eroshenkoam.allure.client.TestCaseService;
import io.github.eroshenkoam.allure.client.dto.Page;
import io.github.eroshenkoam.allure.client.dto.Project;
import io.github.eroshenkoam.allure.client.dto.ScenarioNormalized;
import io.github.eroshenkoam.allure.client.dto.TestCase;
import io.github.eroshenkoam.allure.client.dto.TestCaseAttachment;
import io.github.eroshenkoam.allure.client.dto.scenario.*;
import lombok.Data;
import lombok.experimental.Accessors;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

@CommandLine.Command(
        name = "migrate-testcases",
        mixinStandardHelpOptions = true,
        description = "Migrate test cases from old style"
)
public class MigrateTestCasesCommand extends AbstractTestOpsCommand {

    private static final String META_TIMINGS = "timings.json";
    private static final String RQL_TEST_CASE = "not layer in [\"Shared Steps\", \"Shared\", \"Шаги\", \"Шаг\"]"
            + " and deleted=false";

    private static final Pattern HEADER_PATTERN =
            Pattern.compile("#+ (?<text>.+)");
    private static final Pattern LIST_ITEM_PATTERN =
            Pattern.compile("(?<spaces>[ \\t]*)([*\\-]) (?<text>.+)");
    private static final Pattern TABLE_PATTERN =
            Pattern.compile("^[ \\t]*\\|(.*\\|)+$");

    private static final Pattern ATTACHMENT_PATTERN =
            Pattern.compile("^!\\[(?<alt>[^\\]]*)]\\(/api/rs/testcase/attachment/(?<id>\\d+)/content\\)$");
    private static final Pattern INLINE_ATTACHMENT_PATTERN =
            Pattern.compile("!\\[(?<alt>[^\\]]*)]\\(/api/rs/testcase/attachment/(?<id>\\d+)/content\\)");

    private static final Pattern EXPECTED_PATTERN =
            Pattern.compile("\\{\"action\":\"(?<action>.*)\",\"expected\":\"(?<expected>.*)(\"})+");

    private static final Integer PAGE_SIZE = 1000;

    private static final ObjectMapper MAPPER = new ObjectMapper().disable(FAIL_ON_UNKNOWN_PROPERTIES);

    @CommandLine.Option(
            names = {"--allure.project.id"},
            description = "Allure TestOps project id",
            defaultValue = "${env:ALLURE_PROJECT_ID}",
            split = ","
    )
    protected List<Long> allureProjectIds;

    @CommandLine.Option(
            names = {"--meta.path"},
            description = "Meta information path",
            defaultValue = "${env:META_PATH}"
    )
    protected Path metaPath;

    @Override
    public void runUnsafe(final ServiceBuilder builder) throws Exception {
        final Map<String, List<Long>> timingMeta = new ConcurrentHashMap<>();

        builder.withInterceptor(getTimingCollector(timingMeta));
        builder.withDispatcher(getConcurentDispatcher());

        final TestCaseService tcService = builder.create(TestCaseService.class);
        final TestCaseScenarioService tcScenarioService = builder.create(TestCaseScenarioService.class);

        final List<Long> projectIds = new ArrayList<>();
        if (Objects.nonNull(allureProjectIds) && !allureProjectIds.isEmpty()) {
            projectIds.addAll(allureProjectIds);
        } else {
            final ProjectService projectService = builder.create(ProjectService.class);
            projectIds.addAll(getAllProjects(projectService).keySet());
        }
        for (Long projectId : projectIds) {
            migrateProject(tcService, tcScenarioService, projectId);
        }
        writeMeta(META_TIMINGS, timingMeta);
    }

    private void migrateProject(final TestCaseService tcService,
                                final TestCaseScenarioService tcScenarioService,
                                final Long projectId) throws Exception {
        final Set<Long> testCasesIds = getAllTestCases(tcService, projectId, RQL_TEST_CASE).keySet();

        migrateProjectTestCases(tcScenarioService, testCasesIds, projectId);
    }

    private void migrateProjectTestCases(final TestCaseScenarioService tcScenarioService,
                                         final Set<Long> testCaseIds,
                                         final Long projectId) throws Exception {
        System.out.printf("Found information about %d test cases\n", testCaseIds.size());
        invokeParallel(String.format("[%d] migrate test case expected", projectId), testCaseIds, (id) -> {
            final ScenarioNormalized newScenario = executeRequest(tcScenarioService.getScenario(id));
            if (newScenario == null || newScenario.getScenarioSteps() == null
                    || newScenario.getScenarioSteps().isEmpty()) {
                executeRequest(tcScenarioService.migrateScenario(id));
                migrateExpectedSteps(tcScenarioService, id);
            } else {
                System.out.printf("Test case %d already migrated\n", id);
            }
        });
    }

    private void migrateExpectedSteps(final TestCaseScenarioService scenarioService,
                                      final Long testCaseId) throws Exception {
        final ScenarioNormalized oldScenario = executeRequest(scenarioService.getScenario(testCaseId));
        if (oldScenario == null || oldScenario.getScenarioSteps() == null || oldScenario.getScenarioSteps().isEmpty()) {
            return;
        }
        final AttachmentContext attachmentContext = createAttachmentContext(scenarioService, testCaseId);
        final TestCaseScenarioV2 migratedScenario = migrateScenario(scenarioService, testCaseId, oldScenario, attachmentContext);
        if (migratedScenario == null || migratedScenario.getSteps() == null || migratedScenario.getSteps().isEmpty()) {
            System.out.printf("Scenario for testcase %d migrate with empty result. Skipping\n", testCaseId);
            return;
        }
        System.out.println(new ObjectMapper().writeValueAsString(migratedScenario));
        executeRequest(scenarioService.setScenario(testCaseId, migratedScenario));
    }

    private TestCaseScenarioV2 migrateScenario(final TestCaseScenarioService tcScenarioService,
                                               final Long testCaseId,
                                               final ScenarioNormalized oldScenario,
                                               final AttachmentContext attachmentContext) throws Exception {
        final Map<Long, io.github.eroshenkoam.allure.client.dto.ScenarioStep> steps = oldScenario.getScenarioSteps();
        final List<Long> ids = oldScenario.getRoot().getChildren();
        final List<ScenarioStep> migratedSteps = new ArrayList<>();
        for (final Long id : ids) {
            final io.github.eroshenkoam.allure.client.dto.ScenarioStep oldStep = steps.get(id);
            final String name = StringUtils.trimToNull(oldStep.getBody());
            if (StringUtils.isNoneBlank(name)) {
                final Optional<ScenarioStep> newStep = name.startsWith("expected")
                        ? migrateExpectedStep(tcScenarioService, testCaseId, name, attachmentContext)
                        : migrateBodyStep(id, steps, tcScenarioService, testCaseId, attachmentContext);
                newStep.ifPresent(migratedSteps::add);
            }
        }
        return new TestCaseScenarioV2()
                .setSteps(migratedSteps);
    }

    private Optional<ScenarioStep> migrateBodyStep(final Long stepId,
                                                   final Map<Long, io.github.eroshenkoam.allure.client.dto.ScenarioStep> steps,
                                                   final TestCaseScenarioService tcScenarioService,
                                                   final Long testCaseId,
                                                   final AttachmentContext attachmentContext) throws Exception {
        final io.github.eroshenkoam.allure.client.dto.ScenarioStep oldStep = steps.get(stepId);
        if (StringUtils.isNoneBlank(oldStep.getBody())) {

            final BodyStep step = new BodyStep()
                    .setBody(oldStep.getBody());
            if (Objects.nonNull(oldStep.getChildren())) {
                final List<ScenarioStep> subSteps = new ArrayList<>();
                for (final Long id : oldStep.getChildren()) {
                    final Optional<ScenarioStep> migrated = this.migrateBodyStep(
                            id,
                            steps,
                            tcScenarioService,
                            testCaseId,
                            attachmentContext
                    );
                    migrated.ifPresent(subSteps::add);
                }
                step.setSteps(subSteps);
            }
            return Optional.of(step);
        }
        if (Objects.nonNull(oldStep.getAttachmentId())) {
            final Long sourceAttachmentId = oldStep.getAttachmentId();
            final Optional<Long> attachmentId = resolveAttachmentId(
                    tcScenarioService,
                    testCaseId,
                    sourceAttachmentId,
                    null,
                    attachmentContext
            );
            if (attachmentId.isEmpty()) {
                System.out.printf(
                        "WARN: attachment %d for test case %d could not be resolved, dropping step%n",
                        sourceAttachmentId,
                        testCaseId
                );
                return Optional.empty();
            }
            return Optional.of(new AttachmentStep().setAttachmentId(attachmentId.get()));
        }
        return Optional.empty();
    }

    private Optional<ScenarioStep> migrateExpectedStep(final TestCaseScenarioService tcScenarioService,
                                                       final Long testCaseId,
                                                       final String name,
                                                       final AttachmentContext attachmentContext) throws Exception {
        final Optional<StepExpected> possibleCustomStep = readStepExpected(
                StringUtils.trimToNull(name.replaceFirst("expected", "")));
        if (possibleCustomStep.isEmpty()) {
            return Optional.empty();
        }
        final StepExpected customStep = possibleCustomStep.get();
        final boolean blankStep = StringUtils.isBlank(customStep.getAction())
                && StringUtils.isBlank(customStep.getExpected());
        if (blankStep) {
            return Optional.empty();
        }
        final List<ScenarioStep> actionSteps = getStepsFromText(
                tcScenarioService,
                testCaseId,
                StringUtils.defaultIfBlank(customStep.getAction(), "Action"),
                false,
                attachmentContext
        );
        final ScenarioStep firstActionStep = actionSteps.getFirst();
        final BodyStep bodyStep = firstActionStep instanceof BodyStep
                ? ((BodyStep) firstActionStep).setSteps(actionSteps.subList(1, actionSteps.size()))
                : createBodyStep("Action").setSteps(actionSteps);

        final List<ScenarioStep> expectedResult = getStepsFromText(
                tcScenarioService,
                testCaseId,
                customStep.getExpected(),
                true,
                attachmentContext
        );
        bodyStep.setExpectedResultSteps(expectedResult);
        return Optional.of(bodyStep);
    }

    private List<ScenarioStep> getStepsFromText(final TestCaseScenarioService tcScenarioService,
                                                final Long testCaseId,
                                                final String text,
                                                final boolean expected,
                                                final AttachmentContext attachmentContext) throws Exception {
        final List<ScenarioStep> result = new ArrayList<>();
        final List<ParsedLine> parsedLines = getParsedLines(text);
        for (final ParsedLine line : parsedLines) {
            switch (line.getType()) {
                case CONTENT -> {
                    final ScenarioStep step = expected
                            ? createExpectedBodyStep(line.getContent())
                            : createBodyStep(line.getContent());
                    result.add(step);
                }
                case ATTACHMENT -> addAttachmentStep(
                                tcScenarioService,
                                testCaseId,
                                Long.parseLong(line.getContent()),
                                line.getAttachmentAlt(),
                                attachmentContext
                        )
                        .ifPresent(result::add);
                case TABLE -> {
                    final Long attachmentId = createTable(tcScenarioService, line.getContent(), testCaseId);
                    result.add(new AttachmentStep().setAttachmentId(attachmentId));
                }
            }
        }
        return result;
    }

    private Optional<ScenarioStep> addAttachmentStep(final TestCaseScenarioService tcScenarioService,
                                                     final Long testCaseId,
                                                     final Long attachmentId,
                                                     final String markdownAttachmentAlt,
                                                     final AttachmentContext attachmentContext) {
        return resolveAttachmentId(
                        tcScenarioService,
                        testCaseId,
                        attachmentId,
                        markdownAttachmentAlt,
                        attachmentContext
                )
                .map(id -> new AttachmentStep().setAttachmentId(id));
    }

    private Optional<Long> resolveAttachmentId(final TestCaseScenarioService tcScenarioService,
                                               final Long testCaseId,
                                               final Long attachmentId,
                                               final String markdownAttachmentAlt,
                                               final AttachmentContext attachmentContext) {
        if (attachmentContext.getOwnedAttachmentIds().contains(attachmentId)) {
            return Optional.of(attachmentId);
        }
        if (attachmentContext.getRemappedAttachmentIds().containsKey(attachmentId)) {
            return Optional.of(attachmentContext.getRemappedAttachmentIds().get(attachmentId));
        }
        try (ResponseBody body = executeRequest(tcScenarioService.getAttachmentContent(attachmentId));) {
            if (body == null || body.contentLength() <= 0) {
                System.out.printf("Attachment %d content is missing, skipping%n", attachmentId);
                return Optional.empty();
            }
            final MediaType mediaType = Objects.nonNull(body.contentType())
                    ? body.contentType()
                    : MediaType.parse("application/octet-stream");
            final byte[] content = body.bytes();
            final RequestBody requestBody = RequestBody.create(content, mediaType);
            final String fileName = resolveCopiedAttachmentFileName(testCaseId, markdownAttachmentAlt);
            final MultipartBody.Part copiedAttachment = MultipartBody.Part.createFormData(
                    "file",
                    fileName,
                    requestBody
            );
            final List<TestCaseAttachment> createdAttachments = executeRequest(
                    tcScenarioService.createAttachment(testCaseId, List.of(copiedAttachment))
            );
            if (createdAttachments == null || createdAttachments.isEmpty()) {
                return Optional.empty();
            }
            final Long createdAttachmentId = createdAttachments.getFirst().getId();
            attachmentContext.getRemappedAttachmentIds().put(attachmentId, createdAttachmentId);
            attachmentContext.getOwnedAttachmentIds().add(createdAttachmentId);
            System.out.printf(
                    "Attachment %d does not belong to test case %d. Created copy %d%n",
                    attachmentId,
                    testCaseId,
                    createdAttachmentId
            );
            return Optional.of(createdAttachmentId);
        } catch (Exception e) {
            System.out.printf("Failed to copy attachment %d for test case %d%n", attachmentId, testCaseId);
            return Optional.empty();
        }
    }

    private static String resolveCopiedAttachmentFileName(final Long testCaseId, final String markdownAttachmentAlt) {
        if (StringUtils.isNotBlank(markdownAttachmentAlt)) {
            return markdownAttachmentAlt.trim();
        }
        return String.format("migrated-attachment-%d", testCaseId);
    }

    private AttachmentContext createAttachmentContext(final TestCaseScenarioService tcScenarioService,
                                                      final Long testCaseId) throws IOException {
        final Set<Long> ownedAttachmentIds = ConcurrentHashMap.newKeySet();
        Page<TestCaseAttachment> current = new Page<TestCaseAttachment>().setNumber(-1);
        do {
            current = executeRequest(tcScenarioService.getAttachments(testCaseId, current.getNumber() + 1, PAGE_SIZE));
            if (current == null || current.getContent() == null) {
                break;
            }
            for (final TestCaseAttachment attachment : current.getContent()) {
                ownedAttachmentIds.add(attachment.getId());
            }
        } while (current.getNumber() + 1 < current.getTotalPages());
        return new AttachmentContext()
                .setOwnedAttachmentIds(ownedAttachmentIds)
                .setRemappedAttachmentIds(new ConcurrentHashMap<>());
    }

    private static List<ParsedLine> getParsedLines(final String text) {
        final String preparedText = Optional.ofNullable(text)
                .orElse("empty");
        final List<ParsedLine> lines = Arrays.stream(preparedText.split("\n"))
                .filter(s -> !s.isBlank())
                .map(StringEscapeUtils::unescapeJson)
                .flatMap(line -> parseLineWithAttachments(line).stream())
                .toList();
        return collectLines(lines);
    }

    private static List<ParsedLine> parseLineWithAttachments(final String line) {
        final Matcher attachmentMatcher = INLINE_ATTACHMENT_PATTERN.matcher(line);
        if (!attachmentMatcher.find()) {
            return List.of(parseLine(line));
        }
        final List<ParsedLine> parsed = new ArrayList<>();
        int start = 0;
        do {
            final String before = line.substring(start, attachmentMatcher.start());
            if (!before.isBlank()) {
                parsed.add(parseLine(before));
            }
            final String attachmentId = attachmentMatcher.group("id");
            parsed.add(new ParsedLine()
                    .setContent(attachmentId)
                    .setAttachmentAlt(StringUtils.trimToNull(attachmentMatcher.group("alt")))
                    .setType(LineType.ATTACHMENT));
            start = attachmentMatcher.end();
        } while (attachmentMatcher.find());
        final String after = line.substring(start);
        if (!after.isBlank()) {
            parsed.add(parseLine(after));
        }
        return parsed;
    }

    private static List<ParsedLine> collectLines(final List<ParsedLine> lines) {
        final List<ParsedLine> result = new ArrayList<>();
        if (!lines.isEmpty()) {
            result.add(lines.getFirst());
            for (int i = 1; i < lines.size(); i++) {
                final ParsedLine current = lines.get(i);
                final ParsedLine prevLine = result.get(result.size() - 1);
                if (current.getType().equals(LineType.ATTACHMENT)) {
                    result.add(current);
                } else {
                    if (current.getType().equals(prevLine.getType())) {
                        prevLine.setContent(String.format("%s\n%s", prevLine.getContent(), current.getContent()));
                    } else {
                        if (prevLine.getType().equals(LineType.CONTENT)) {
                            prevLine.setContent(prevLine.getContent());
                        }
                        final ParsedLine newLine = new ParsedLine()
                                .setType(current.getType())
                                .setContent(current.getContent())
                                .setAttachmentAlt(current.getAttachmentAlt());
                        result.add(newLine);
                    }
                }
            }
        }
        return result;
    }

    private static ParsedLine parseLine(final String line) {
        final Matcher attachmentMatcher = ATTACHMENT_PATTERN.matcher(line);
        if (attachmentMatcher.matches()) {
            final long attachmentId = Long.parseLong(attachmentMatcher.group("id"));
            return new ParsedLine()
                    .setContent(Long.toString(attachmentId))
                    .setAttachmentAlt(StringUtils.trimToNull(attachmentMatcher.group("alt")))
                    .setType(LineType.ATTACHMENT);
        }
        final Matcher headerMatcher = HEADER_PATTERN.matcher(line);
        if (headerMatcher.matches()) {
            final String text = headerMatcher.group("text");
            return new ParsedLine()
                    .setType(LineType.CONTENT)
                    .setContent(String.format("**%s**", text));
        }
        final Matcher listItemMatcher = LIST_ITEM_PATTERN.matcher(line);
        if (listItemMatcher.matches()) {
            final String text = listItemMatcher.group("text");
            final String spaces = listItemMatcher.group("spaces");
            return new ParsedLine()
                    .setType(LineType.CONTENT)
                    .setContent(String.format("%s- %s", spaces, text));
        }
        final Matcher tableMatcher = TABLE_PATTERN.matcher(line);
        if (tableMatcher.matches()) {
            final String csv = line.trim().substring(1, line.length() - 2).replace("|", ",");
            return new ParsedLine()
                    .setType(LineType.TABLE)
                    .setContent(csv);
        }
        return new ParsedLine()
                .setType(LineType.CONTENT)
                .setContent(line);
    }

    private static Long createTable(final TestCaseScenarioService tcService,
                                    final String content,
                                    final Long testCaseId) throws IOException {
        final RequestBody requestBody = RequestBody.create(
                MediaType.parse("text/csv"),
                content.getBytes(StandardCharsets.UTF_8)
        );
        final MultipartBody.Part create = MultipartBody.Part
                .createFormData("file", "Table", requestBody);
        final List<TestCaseAttachment> createdAttachment = executeRequest(
                tcService.createAttachment(testCaseId, Arrays.asList(create))
        );
        return createdAttachment.get(0).getId();
    }

    private static Map<Long, String> getAllProjects(final ProjectService service) throws IOException {
        final Map<Long, String> result = new HashMap<>();
        Page<Project> current = new Page<Project>().setNumber(-1);
        do {
            current = executeRequest(service.getProjects("", current.getNumber() + 1, PAGE_SIZE));
            for (Project item : current.getContent()) {
                result.put(item.getId(), item.getName());
            }
        } while (current.getNumber() < current.getTotalPages());
        return result;
    }

    private static Map<Long, String> getAllTestCases(final TestCaseService service,
                                                     final Long projectId,
                                                     final String filter) throws IOException {
        final Map<Long, String> result = new HashMap<>();
        Page<TestCase> current = new Page<TestCase>().setNumber(-1);
        do {
            current = executeRequest(service.findByRql(projectId, filter, current.getNumber() + 1, PAGE_SIZE));
            for (TestCase item : current.getContent()) {
                result.put(item.getId(), item.getName());
            }
        } while (current.getNumber() < current.getTotalPages());
        current = new Page<TestCase>().setNumber(-1);
        do {
            current = executeRequest(service.findByRql(projectId, filter, true, current.getNumber() + 1, PAGE_SIZE));
            for (TestCase item : current.getContent()) {
                result.put(item.getId(), item.getName());
            }
        } while (current.getNumber() < current.getTotalPages());
        return result;
    }

    private static Optional<StepExpected> readStepExpected(final String content) {
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

    private static BodyStep createBodyStep(final String body) {
        return new BodyStep()
                .setBodyJson(MarkdownToJsonConverter.convertToJson(body))
                .setBody(body);
    }

    private static ExpectedBodyStep createExpectedBodyStep(final String body) {
        return new ExpectedBodyStep()
                .setBodyJson(MarkdownToJsonConverter.convertToJson(body))
                .setBody(body);
    }

    private <T> void writeMeta(final String name, final T type) throws IOException {
        if (Objects.isNull(metaPath)) {
            return;
        }
        final Path file = metaPath.resolve(name);
        Files.createDirectories(metaPath);
        MAPPER.writeValue(file.toFile(), type);
    }

    private Dispatcher getConcurentDispatcher() {
        final int threads = getTreadCount();
        Dispatcher dispatcher = new Dispatcher(Executors.newFixedThreadPool(threads));
        dispatcher.setMaxRequests(threads);
        dispatcher.setMaxRequestsPerHost(threads);
        return dispatcher;
    }

    private Interceptor getTimingCollector(final Map<String, List<Long>> timingMeta) {
        return (chain) -> {
            final long start = System.currentTimeMillis();
            final Request request = chain.request();
            try {
                return chain.proceed(request);
            } finally {
                final String url = String.format("%s %s", request.method(), request.url());
                final List<Long> times = timingMeta.getOrDefault(url, new ArrayList<>());
                final long stop = System.currentTimeMillis();
                times.add(stop - start);
                timingMeta.put(url, times);
            }
        };
    }

    private enum LineType {
        TABLE,
        ATTACHMENT,
        CONTENT
    }

    @Data
    @Accessors(chain = true)
    private static class ParsedLine {
        private LineType type;
        private String content;
        private String attachmentAlt;
    }

    @Data
    @Accessors(chain = true)
    private static class StepExpected {
        private String action;
        private String expected;
    }

    @Data
    @Accessors(chain = true)
    private static class TableStep implements ScenarioStep {
        private String table;
    }

    @Data
    @Accessors(chain = true)
    private static class AttachmentContext {
        private Set<Long> ownedAttachmentIds;
        private Map<Long, Long> remappedAttachmentIds;
    }

}
