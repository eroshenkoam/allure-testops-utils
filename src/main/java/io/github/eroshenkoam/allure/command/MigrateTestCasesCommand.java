package io.github.eroshenkoam.allure.command;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eroshenkoam.allure.textmarkup.MarkdownToJsonConverter;
import io.qameta.allure.ee.client.ProjectService;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.SharedStepScenarioService;
import io.qameta.allure.ee.client.SharedStepService;
import io.qameta.allure.ee.client.TestCaseScenarioService;
import io.qameta.allure.ee.client.TestCaseService;
import io.qameta.allure.ee.client.dto.Page;
import io.qameta.allure.ee.client.dto.Project;
import io.qameta.allure.ee.client.dto.ScenarioNormalized;
import io.qameta.allure.ee.client.dto.ScenarioStep;
import io.qameta.allure.ee.client.dto.ScenarioStepCreate;
import io.qameta.allure.ee.client.dto.ScenarioStepResponse;
import io.qameta.allure.ee.client.dto.ScenarioStepUpdate;
import io.qameta.allure.ee.client.dto.SharedStepAttachment;
import io.qameta.allure.ee.client.dto.SharedStepCreate;
import io.qameta.allure.ee.client.dto.TestCase;
import io.qameta.allure.ee.client.dto.TestCaseAttachment;
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
import java.util.Collection;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

@CommandLine.Command(
        name = "migrate-testcases",
        mixinStandardHelpOptions = true,
        description = "Migrate test cases from old style"
)
public class MigrateTestCasesCommand extends AbstractTestOpsCommand {

    private static final String META_PROJECTS = "projects.json";
    private static final String META_TIMINGS = "timings.json";
    private static final String META_STEPS = "steps.json";

    private static final String RQL_TEST_CASE = "not layer in [\"Shared Steps\", \"Shared\", \"Шаги\", \"Шаг\"]";
    private static final String RQL_SHARED_STEP = "layer in [\"Shared Steps\", \"Shared\", \"Шаги\", \"Шаг\"]";

    private static final Pattern HEADER_PATTERN =
            Pattern.compile("#+ (?<text>.+)");
    private static final Pattern LIST_ITEM_PATTERN =
            Pattern.compile("(?<spaces>[ \\t]*)([*\\-]) (?<text>.+)");
    private static final Pattern TABLE_PATTERN =
            Pattern.compile("^[ \\t]*\\|(.*\\|)+$");

    private static final Pattern ATTACHMENT_PATTERN =
            Pattern.compile("!\\[(.*)]\\(/api/rs/testcase/attachment/(?<id>\\d*)/content\\)");

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
            names = {"--thread.count"},
            description = "Thread count",
            defaultValue = "${env:THREAD_COUNT}"
    )
    protected Integer threadCount;

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
        final SharedStepService ssService = builder.create(SharedStepService.class);
        final SharedStepScenarioService ssScenarioService = builder.create(SharedStepScenarioService.class);

        // @formatter:off
        final Map<Long, Boolean> projectMeta = readMeta(META_PROJECTS, new TypeReference<Map<Long, Boolean>>() {})
                .orElseGet(HashMap::new);
        // @formatter:on

        final List<Long> projectIds = new ArrayList<>();
        if (Objects.nonNull(allureProjectIds) && !allureProjectIds.isEmpty()) {
            projectIds.addAll(allureProjectIds);
        } else {
            final ProjectService projectService = builder.create(ProjectService.class);
            projectIds.addAll(getAllProjects(projectService).keySet());
        }
        for (Long projectId : projectIds) {
            if (projectMeta.getOrDefault(projectId, false)) {
                System.out.printf("Project with id '%s' already migrated\n", projectId);
            } else {
                System.out.printf("Migrate project with id '%s'\n", projectId);
                migrateProject(
                        tcService, tcScenarioService, ssService, ssScenarioService, projectId
                );
                projectMeta.put(projectId, true);
            }
        }
        writeMeta(META_PROJECTS, projectMeta);
        writeMeta(META_TIMINGS, timingMeta);
    }

    private void migrateProject(final TestCaseService tcService,
                                final TestCaseScenarioService tcScenarioService,
                                final SharedStepService ssService,
                                final SharedStepScenarioService ssScenarioService,
                                final Long projectId) throws Exception {
        final Set<Long> testCasesIds = getAllTestCases(tcService, projectId, RQL_TEST_CASE).keySet();
        final Set<Long> sharedStepsIds = getAllTestCases(tcService, projectId, RQL_SHARED_STEP).keySet();

        final Set<Long> allIds = Stream.concat(testCasesIds.stream(), sharedStepsIds.stream())
                .collect(Collectors.toSet());

        migrateProjectTestCases(tcScenarioService, allIds, projectId);
        final Map<Long, Long> sharedSteps = migrateProjectSharedSteps(
                tcService, ssService, tcScenarioService, ssScenarioService, sharedStepsIds, projectId
        );
        migrateProjectSharedSteps(tcScenarioService, sharedSteps, testCasesIds, projectId);
        deleteProjectSharedSteps(tcService, sharedSteps, projectId);
    }

    private void migrateProjectTestCases(final TestCaseScenarioService tcScenarioService,
                                         final Set<Long> testCaseIds,
                                         final Long projectId) throws Exception {
        System.out.printf("Found information about %d test cases\n", testCaseIds.size());
        invokeParallel(String.format("[%d] migrate test case expected", projectId), testCaseIds, (id) -> {
            executeRequest(tcScenarioService.migrateScenario(id));
            migrateExpectedSteps(tcScenarioService, id);
        });
    }

    private void migrateProjectSharedSteps(final TestCaseScenarioService tcScenarioService,
                                           final Map<Long, Long> sharedSteps,
                                           final Set<Long> testCaseIds,
                                           final Long projectId) throws Exception {
        System.out.printf("Found information about %d test cases\n", testCaseIds.size());
        invokeParallel(String.format("[%d] migrate project shared steps", projectId), testCaseIds, (id) -> {
            migrateSharedSteps(tcScenarioService, id, sharedSteps);
        });
    }

    private void deleteProjectSharedSteps(final TestCaseService tcService,
                                          final Map<Long, Long> sharedSteps,
                                          final Long projectId) throws Exception {
        System.out.printf("Delete old %d shared steps\n", sharedSteps.size());
        invokeParallel(String.format("[%d] delete old shared steps", projectId), sharedSteps.keySet(), (id) -> {
            executeRequest(tcService.delete(id));
        });
    }

    private Map<Long, Long> migrateProjectSharedSteps(final TestCaseService tcService,
                                                      final SharedStepService ssService,
                                                      final TestCaseScenarioService tcScenarioService,
                                                      final SharedStepScenarioService ssScenarioService,
                                                      final Set<Long> sharedStepIds,
                                                      final Long projectId) throws Exception {
        // @formatter:off
        final Map<Long, Long> stepsMeta = readMeta(META_STEPS, new TypeReference<Map<Long, Long>>() {})
                .orElseGet(HashMap::new);
        // @formatter:on

        final Map<Long, Long> steps = new ConcurrentHashMap<>();
        System.out.printf("Found information about %d shared steps\n", sharedStepIds.size());
        invokeParallel(String.format("[%d] migrate shared steps", projectId), sharedStepIds, (testCaseId) -> {
            final Long sharedStepId = migrateSharedStep(
                    tcService,
                    ssService,
                    tcScenarioService,
                    ssScenarioService,
                    stepsMeta,
                    testCaseId,
                    projectId
            );
            steps.put(testCaseId, sharedStepId);
        });
        stepsMeta.putAll(steps);
        writeMeta(META_STEPS, stepsMeta);

        return steps;
    }

    private Long migrateSharedStep(final TestCaseService tcService,
                                   final SharedStepService ssService,
                                   final TestCaseScenarioService tcScenarioService,
                                   final SharedStepScenarioService ssScenarioService,
                                   final Map<Long, Long> stepsMeta,
                                   final Long testCaseId,
                                   final Long projectId) throws Exception {
        final Optional<Long> existingStep = Optional.ofNullable(stepsMeta.get(testCaseId));
        if (existingStep.isPresent()) {
            final Long sharedStepId = existingStep.get();
            System.out.printf("Found existing shared step %s = %s\n", testCaseId, sharedStepId);
            copySharedStepScenario(tcScenarioService, ssScenarioService, testCaseId, sharedStepId);
            return sharedStepId;
        } else {
            System.out.printf("Migrating shared step with id %s\n", testCaseId);
            final Long sharedStepId = createSharedStep(tcService, ssService, testCaseId, projectId);
            copySharedStepScenario(tcScenarioService, ssScenarioService, testCaseId, sharedStepId);
            System.out.printf("Shared step successfully migrated with id %s\n", sharedStepId);
            return sharedStepId;
        }
    }

    private void migrateExpectedSteps(final TestCaseScenarioService scenarioService,
                                      final Long testCaseId) throws Exception {
        final ScenarioNormalized scenario = executeRequest(scenarioService.getScenario(testCaseId));
        final Collection<ScenarioStep> steps = scenario.getScenarioSteps().values();
        for (ScenarioStep step : steps) {
            migrateExpectedStep(scenarioService, step, testCaseId);
        }
    }

    private void migrateExpectedStep(final TestCaseScenarioService scenarioService,
                                     final ScenarioStep step,
                                     final Long testCaseId) throws IOException {
        final String name = StringUtils.trimToNull(step.getBody());
        if (!(Objects.nonNull(name) && name.startsWith("expected"))) {
            return;
        }
        final Optional<StepExpected> possibleCustomStep = readStepExpected(
                StringUtils.trimToNull(name.replaceFirst("expected", "")));
        if (possibleCustomStep.isEmpty()) {
            return;
        }
        final StepExpected customStep = possibleCustomStep.get();
        final boolean blankStep = StringUtils.isBlank(customStep.getAction())
                && StringUtils.isBlank(customStep.getExpected());
        if (blankStep) {
            executeRequest(scenarioService.deleteStep(step.getId()));
        } else {
            final List<ScenarioStepCreate> action = getStepsFromText(
                    scenarioService, StringUtils.defaultIfBlank(customStep.getAction(), "Action"), testCaseId
            );
            final ScenarioStepUpdate update = new ScenarioStepUpdate()
                    .setBody(action.getFirst().getBody())
                    .setBodyJson(action.getFirst().getBodyJson());
            final boolean withExpectedResult = StringUtils.isNotBlank(customStep.getExpected());
            final ScenarioNormalized updatedScenario = executeRequest(
                    scenarioService.updateStep(step.getId(), update, withExpectedResult)
            );
            if (action.size() > 1) {
                for (ScenarioStepCreate subStep : action.subList(1, action.size())) {
                    executeRequest(scenarioService.createStep(subStep.setParentId(step.getId()), null, null));
                }
            }
            if (withExpectedResult) {
                final Long expectedResultId = updatedScenario.getScenarioSteps().get(step.getId())
                        .getExpectedResultId();
                final List<ScenarioStepCreate> expectedResult = getStepsFromText(
                        scenarioService, customStep.getExpected(), testCaseId
                );
                for (ScenarioStepCreate subStep : expectedResult) {
                    executeRequest(scenarioService.createStep(subStep.setParentId(expectedResultId), null, null));
                }
            }
        }
    }

    private void migrateSharedSteps(final TestCaseScenarioService scenarioService,
                                    final Long testCaseId,
                                    final Map<Long, Long> sharedSteps) throws Exception {
        final ScenarioNormalized scenario = executeRequest(scenarioService.getScenario(testCaseId));
        final Collection<ScenarioStep> steps = scenario.getScenarioSteps().values();
        for (ScenarioStep step : steps) {
            final String name = step.getBody();
            if (Objects.nonNull(name) && name.startsWith("shared")) {
                final StepShared customStep = MAPPER.readValue(
                        name.replaceFirst("shared", "").trim(), StepShared.class
                );
                final ScenarioStepUpdate update = new ScenarioStepUpdate()
                        .setBody(null)
                        .setExpectedResult(null)
                        .setAttachmentId(null)
                        .setSharedStepId(sharedSteps.get(customStep.getTestCaseId()));
                executeRequest(scenarioService.updateStep(step.getId(), update));
            }
        }
    }

    private Long createSharedStep(final TestCaseService testCaseService,
                                  final SharedStepService sharedStepService,
                                  final Long testCaseId,
                                  final Long projectId) throws Exception {
        final TestCase testCase = executeRequest(testCaseService.findById(testCaseId));
        final SharedStepCreate createRequest = new SharedStepCreate()
                .setName(testCase.getName())
                .setProjectId(projectId);
        return executeRequest(sharedStepService.createStep(createRequest)).getId();
    }

    private void copySharedStepScenario(final TestCaseScenarioService tcScenarioService,
                                        final SharedStepScenarioService ssScenarioService,
                                        final Long testCaseId,
                                        final Long sharedStepId) throws Exception {
        final ScenarioNormalized scenario = executeRequest(tcScenarioService.getScenario(testCaseId));
        deleteSharedStepContent(ssScenarioService, sharedStepId);
        final Map<Long, Long> attachments = copySharedStepAttachments(
                tcScenarioService, ssScenarioService, testCaseId, sharedStepId
        );

        final ScenarioStep root = scenario.getRoot();
        copySharedStepSteps(
                ssScenarioService,
                scenario.getScenarioSteps(),
                attachments,
                sharedStepId,
                root.getChildren(),
                null
        );
    }

    private void deleteSharedStepContent(final SharedStepScenarioService ssScenarioService,
                                         final Long sharedStepId) throws Exception {
        final ScenarioNormalized scenario = executeRequest(ssScenarioService.getScenario(sharedStepId));
        if (Objects.nonNull(scenario.getRoot().getChildren())) {
            for (Long stepId : scenario.getRoot().getChildren()) {
                executeRequest(ssScenarioService.deleteStep(stepId));
            }
        }
        final Page<SharedStepAttachment> attachmentsPage = executeRequest(
                ssScenarioService.getAttachments(sharedStepId, 0, 1000)
        );
        if (Objects.nonNull(attachmentsPage)) {
            final List<Long> attachments = attachmentsPage.getContent().stream()
                    .map(SharedStepAttachment::getId)
                    .toList();
            for (Long attachmentId : attachments) {
                executeRequest(ssScenarioService.deleteAttachment(attachmentId));
            }
        }
    }

    private Map<Long, Long> copySharedStepAttachments(final TestCaseScenarioService tcScenarioService,
                                                      final SharedStepScenarioService ssScenarioService,
                                                      final Long testCaseId,
                                                      final Long sharedStepId) throws Exception {
        final Map<Long, Long> result = new HashMap<>();

        final Page<TestCaseAttachment> attachmentsPage = executeRequest(
                tcScenarioService.getAttachments(testCaseId, 0, 1000)
        );
        final List<TestCaseAttachment> attachments = attachmentsPage.getContent();
        for (TestCaseAttachment attachment : attachments) {
            final Optional<Long> copiedAttachmentId = copSharedStepAttachment(
                    tcScenarioService, ssScenarioService, attachment, sharedStepId
            );
            copiedAttachmentId.ifPresent(id -> result.put(attachment.getId(), id));
        }
        return result;
    }

    private Optional<Long> copSharedStepAttachment(final TestCaseScenarioService tcScenarioService,
                                                   final SharedStepScenarioService ssScenarioService,
                                                   final TestCaseAttachment attachment,
                                                   final Long sharedStepId) {
        try (ResponseBody content = executeRequest(tcScenarioService.getAttachmentContent(attachment.getId()))) {
            final RequestBody requestBody = RequestBody.create(
                    MediaType.parse(attachment.getContentType()),
                    content.bytes()
            );
            final MultipartBody.Part create = MultipartBody.Part
                    .createFormData("file", attachment.getName(), requestBody);
            final List<SharedStepAttachment> createdAttachment = executeRequest(
                    ssScenarioService.createAttachment(sharedStepId, Arrays.asList(create))
            );
            return Optional.of(createdAttachment.get(0).getId());
        } catch (Throwable e) {
            return Optional.empty();
        }
    }

    private void copySharedStepSteps(final SharedStepScenarioService ssScenarioService,
                                     final Map<Long, ScenarioStep> stepContext,
                                     final Map<Long, Long> attachmentContext,
                                     final Long sharedStepId,
                                     final List<Long> children,
                                     final Long parentId) throws Exception {
        if (Objects.isNull(children)) {
            return;
        }
        Long createdId = null;
        for (Long currentStepId : children) {
            final ScenarioStep currentStep = stepContext.get(currentStepId);
            ScenarioStepCreate createRequest = new ScenarioStepCreate()
                    .setSharedStepId(sharedStepId)
                    .setParentId(parentId);
            ScenarioStepResponse createdScenario;
            if (Objects.nonNull(currentStep.getAttachmentId())) {
                final Long attachmentId = attachmentContext.get(currentStep.getAttachmentId());
                if (Objects.nonNull(attachmentId)) {
                    createdScenario = executeRequest(
                            ssScenarioService.createStep(createRequest.setAttachmentId(attachmentId), null, createdId)
                    );
                } else {
                    final String message = String.format(
                            "Missing attachment with id '%s'", currentStep.getAttachmentId()
                    );
                    createdScenario = executeRequest(
                            ssScenarioService.createStep(createRequest.setBody(message), null, createdId)
                    );
                }
            } else {
                final Optional<Long> expectedResult = Optional.ofNullable(currentStep.getExpectedResultId());
                createRequest = createRequest.setBody(currentStep.getBody());
                createdScenario = executeRequest(
                        ssScenarioService.createStep(createRequest, null, createdId, expectedResult.isPresent())
                );
                if (expectedResult.isPresent()) {
                    final Long expectedResultId = createdScenario.getScenario().getSharedStepScenarioSteps()
                            .get(createdScenario.getCreatedStepId())
                            .getExpectedResultId();
                    final List<Long> expectedResultChildren = stepContext
                            .get(currentStep.getExpectedResultId())
                            .getChildren();
                    copySharedStepSteps(
                            ssScenarioService,
                            stepContext,
                            attachmentContext,
                            sharedStepId,
                            expectedResultChildren,
                            expectedResultId);
                }
            }
            createdId = createdScenario.getCreatedStepId();
            copySharedStepSteps(
                    ssScenarioService,
                    stepContext,
                    attachmentContext,
                    sharedStepId,
                    currentStep.getChildren(),
                    createdId
            );
        }
    }

    private static List<ScenarioStepCreate> getStepsFromText(final TestCaseScenarioService tcScenarioService,
                                                             final String text,
                                                             final Long testCaseId) throws IOException {
        final List<ScenarioStepCreate> result = new ArrayList<>();
        final List<ParsedLine> parsedLines = getParsedLines(text);
        for (final ParsedLine line : parsedLines) {
            final ScenarioStepCreate create = new ScenarioStepCreate()
                    .setTestCaseId(testCaseId);
            switch (line.getType()) {
                case CONTENT -> create.setBody(line.getContent())
                                        .setBodyJson(MarkdownToJsonConverter.convertToJson(line.getContent()));
                case ATTACHMENT -> create.setAttachmentId(Long.parseLong(line.getContent()));
                case TABLE -> {
                    final Long attachmentId = createTable(tcScenarioService, line.getContent(), testCaseId);
                    create.setAttachmentId(attachmentId);
                }
            }
            result.add(create);
        }
        return result;
    }

    private static List<ParsedLine> getParsedLines(final String text) {
        final String preparedText = Optional.ofNullable(text)
                .orElse("empty");
        final List<ParsedLine> lines = Arrays.stream(preparedText.split("\n"))
                .filter(s -> !s.isBlank())
                .map(StringEscapeUtils::unescapeJson)
                .map(MigrateTestCasesCommand::parseLine)
                .toList();
        return collectLines(lines);
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
                                .setContent(current.getContent());
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


    private <T> Optional<T> readMeta(final String name, final TypeReference<T> type) throws IOException {
        final Path file = metaPath.resolve(name);
        Files.createDirectories(metaPath);
        if (Files.exists(file)) {
            return Optional.of(MAPPER.readValue(file.toFile(), type));
        }
        return Optional.empty();
    }

    private <T> void writeMeta(final String name, final T type) throws IOException {
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
    }

    @Data
    @Accessors(chain = true)
    private static class StepShared {
        private Long testCaseId;
        private Boolean expandScenario;
    }

    @Data
    @Accessors(chain = true)
    private static class StepExpected {
        private String action;
        private String expected;
    }

    @FunctionalInterface
    public interface Consumer<T> {

        void accept(T t) throws Exception;

    }

}
