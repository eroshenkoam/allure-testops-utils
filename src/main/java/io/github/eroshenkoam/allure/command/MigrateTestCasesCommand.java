package io.github.eroshenkoam.allure.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eroshenkoam.allure.textmarkup.MarkdownToJsonConverter;
import io.qameta.allure.ee.client.ProjectService;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.TestCaseScenarioService;
import io.qameta.allure.ee.client.TestCaseService;
import io.qameta.allure.ee.client.dto.Page;
import io.qameta.allure.ee.client.dto.Project;
import io.qameta.allure.ee.client.dto.ScenarioNormalized;
import io.qameta.allure.ee.client.dto.ScenarioStep;
import io.qameta.allure.ee.client.dto.TestCase;
import io.qameta.allure.ee.client.dto.TestCaseAttachment;
import io.qameta.allure.ee.client.dto.scenario.AttachmentStep;
import io.qameta.allure.ee.client.dto.scenario.BodyStep;
import io.qameta.allure.ee.client.dto.scenario.TestCaseScenarioV2;
import lombok.Data;
import lombok.experimental.Accessors;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.Request;
import okhttp3.RequestBody;
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

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

@CommandLine.Command(
        name = "migrate-testcases",
        mixinStandardHelpOptions = true,
        description = "Migrate test cases from old style"
)
public class MigrateTestCasesCommand extends AbstractTestOpsCommand {

    private static final String META_TIMINGS = "timings.json";
    private static final String RQL_TEST_CASE = "not layer in [\"Shared Steps\", \"Shared\", \"Шаги\", \"Шаг\"]";

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
            migrateProject(
                    tcService, tcScenarioService, projectId
            );
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
            executeRequest(tcScenarioService.migrateScenario(id));
            migrateExpectedSteps(tcScenarioService, id);
        });
    }

    private void migrateExpectedSteps(final TestCaseScenarioService scenarioService,
                                      final Long testCaseId) throws Exception {
        final ScenarioNormalized oldScenario = executeRequest(scenarioService.getScenario(testCaseId));
        final TestCaseScenarioV2 migratedScenario = migrateScenario(oldScenario);
        for (int i = 0; i < migratedScenario.getSteps().size(); i++) {
            final io.qameta.allure.ee.client.dto.scenario.ScenarioStep step = migratedScenario.getSteps().get(i);
            if (step instanceof TableStep) {
                final Long attachmentId = createTable(scenarioService, ((TableStep) step).getTable(), testCaseId);
                migratedScenario.getSteps().set(i, new AttachmentStep().setAttachmentId(attachmentId));
            }
        }
        scenarioService.setScenario(testCaseId, migratedScenario);
    }

    private TestCaseScenarioV2 migrateScenario(final ScenarioNormalized oldScenario) throws Exception {
        final TestCaseScenarioV2 migratedScenario = new TestCaseScenarioV2()
                .setSteps(new ArrayList<>());
        final Collection<ScenarioStep> steps = oldScenario.getScenarioSteps().values();
        for (ScenarioStep step : steps) {
            migrateScenarioStep(step).ifPresent(migratedScenario.getSteps()::add);
        }

        return migratedScenario;
    }

    private Optional<io.qameta.allure.ee.client.dto.scenario.ScenarioStep> migrateScenarioStep(final ScenarioStep step) {
        final List<io.qameta.allure.ee.client.dto.scenario.ScenarioStep> steps = new ArrayList<>();
        final String name = StringUtils.trimToNull(step.getBody());
        if (StringUtils.isBlank(name)) {
            return Optional.empty();
        }
        if (!(Objects.nonNull(name) && name.startsWith("expected"))) {
            return Optional.of(new BodyStep().setBody(step.getBody()));
        }
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
        final List<io.qameta.allure.ee.client.dto.scenario.ScenarioStep> actionSteps = getStepsFromText(
                StringUtils.defaultIfBlank(customStep.getAction(), "Action")
        );
        final io.qameta.allure.ee.client.dto.scenario.ScenarioStep firstActionStep = actionSteps.getFirst();
        final BodyStep bodyStep = firstActionStep instanceof BodyStep
                ? ((BodyStep) firstActionStep).setSteps(actionSteps.subList(1, actionSteps.size()))
                : createBodyStep("Action").setSteps(actionSteps);

        final List<io.qameta.allure.ee.client.dto.scenario.ScenarioStep> expectedResult = getStepsFromText(
                customStep.getExpected()
        );
        bodyStep.setExpectedResultSteps(expectedResult);
        return Optional.of(bodyStep);
    }

    private static List<io.qameta.allure.ee.client.dto.scenario.ScenarioStep> getStepsFromText(final String text) {
        final List<io.qameta.allure.ee.client.dto.scenario.ScenarioStep> result = new ArrayList<>();
        final List<ParsedLine> parsedLines = getParsedLines(text);
        for (final ParsedLine line : parsedLines) {
            switch (line.getType()) {
                case CONTENT -> {
                    result.add(createBodyStep(line.getContent()));
                }
                case ATTACHMENT -> {
                    result.add(new AttachmentStep().setAttachmentId(Long.parseLong(line.getContent())));
                }
                case TABLE -> {
                    result.add(new TableStep().setTable(line.getContent()));
                }
            }
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

    private static BodyStep createBodyStep(final String body) {
        return new BodyStep().setBody(body).setBodyJson(MarkdownToJsonConverter.convertToJson(body));
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
    private static class StepExpected {
        private String action;
        private String expected;
    }

    @Data
    @Accessors(chain = true)
    private static class TableStep implements io.qameta.allure.ee.client.dto.scenario.ScenarioStep {
        private String table;
    }

}
