package io.github.eroshenkoam.allure.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.TestCaseService;
import io.qameta.allure.ee.client.dto.*;
import io.qameta.allure.model.Attachment;
import io.qameta.allure.model.Label;
import io.qameta.allure.model.StepResult;
import io.qameta.allure.model.TestResult;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import picocli.CommandLine;
import retrofit2.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static okhttp3.MediaType.parse;
import static okhttp3.RequestBody.create;

@CommandLine.Command(
        name = "sync-testcases", mixinStandardHelpOptions = true,
        description = "Sync test cases from allure results"
)
public class SyncTestCasesCommand extends AbstractTestOpsCommand {

    private static final String LABEL_ALLURE_MANUAL = "ALLURE_MANUAL";

    private static final String LABEL_TAGS = "precondition";

    private static final String LABEL_PRECONDITION = "precondition";
    private static final String LABEL_EXPECTED_RESULT = "expectedResult";

    @CommandLine.Option(
            names = {"--allure.project.id"},
            description = "Allure TestOps project id",
            defaultValue = "${env:ALLURE_PROJECT_ID}",
            required = true
    )
    protected Long allureProjectId;

    @CommandLine.Option(
            names = {"--allure.results"},
            description = "Allure Results Dir",
            defaultValue = "${env:ALLURE_RESULTS_DIR}",
            required = true
    )
    protected Path allureResultsDir;

    @Override
    public void runUnsafe(final ServiceBuilder builder) throws Exception {
        final TestCaseService service = builder.create(TestCaseService.class);

        final List<TestResult> results = getResults(allureResultsDir);
        final Set<String> fullNames = results.stream()
                .map(TestResult::getFullName)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (results.size() < fullNames.size()) {
            System.out.println("Full name required for all allure results");
            return;
        }

        final Map<String, Long> fullNamesWithId = getFullNamesWithIds(service, allureProjectId, "true");

        createTestCases(
                service,
                allureProjectId,
                results.stream()
                        .filter(testResult -> !fullNamesWithId.containsKey(testResult.getFullName()))
                        .collect(Collectors.toList())
        );
        updateTestCases(
                service,
                allureProjectId,
                results.stream()
                        .filter(testResult -> fullNamesWithId.containsKey(testResult.getFullName()))
                        .collect(Collectors.toList())
        );
    }

    private Map<String, Long> getFullNamesWithIds(final TestCaseService service,
                                                  final Long projectId,
                                                  final String filter) throws IOException {
        final Map<String, Long> testCases = new HashMap<>();
        Page<TestCase> current = new Page<TestCase>().setNumber(-1);
        do {
            final Response<Page<TestCase>> response = service
                    .findByRql(projectId, filter, current.getNumber() + 1, 1000)
                    .execute();
            if (!response.isSuccessful()) {
                throw new RuntimeException("Can not find launches: " + response.message());
            }
            current = response.body();
            for (TestCase item : current.getContent()) {
                testCases.put(item.getFullName(), item.getId());
            }
        } while (current.getNumber() < current.getTotalPages());
        return testCases;
    }

    private void createTestCases(final TestCaseService service,
                                 final Long projectId,
                                 final List<TestResult> results) throws IOException {
        System.out.printf("Create %s test results\n", results.size());
        for (final TestResult result : results) {
            System.out.printf("Creating test case with full name '%s'\n", result.getFullName());
            final TestCase testCase = getTestCase(result, projectId);

            final Response<TestCase> createResponse = service.create(testCase).execute();
            if (!createResponse.isSuccessful()) {
                throw new RuntimeException(createResponse.message());
            }

            final Long testCaseId = createResponse.body().getId();
            final TestCaseScenario scenario = new TestCaseScenario()
                    .setSteps(getSteps(result.getSteps(), new HashMap<>()));
            final Response<Void> scenarioResponse = service.setScenario(testCaseId, scenario).execute();
            if (!scenarioResponse.isSuccessful()) {
                throw new RuntimeException(scenarioResponse.message());
            }
        }
    }

    private void updateTestCases(final TestCaseService service,
                                 final Long projectId,
                                 final List<TestResult> results) throws IOException {
        System.out.printf("Update %s test results\n", results.size());
        for (final TestResult result : results) {
            System.out.printf("Updating test case with full name '%s'\n", result.getFullName());
            final List<Long> testCases = getTestCases(
                    service, projectId, String.format("fullName = \"%s\"", result.getFullName())
            );
            if (testCases.size() != 1) {
                throw new RuntimeException(String.format("Invalid full name %s", result.getFullName()));
            }

            final Long testCaseId = testCases.get(0);
            System.out.printf("Updating test case with id '%s'\n", testCaseId);

            final TestCase testCase = getTestCase(result, projectId);
            final Response<TestCase> updateResponse = service.update(testCaseId, testCase).execute();
            if (!updateResponse.isSuccessful()) {
                throw new RuntimeException(updateResponse.message());
            }

            System.out.printf("Updating test case attachments with id '%s'\n", testCaseId);
            final List<Attachment> attachments = getAttachments(result.getSteps());
            final Map<String, TestCaseAttachment> attachmentsContext = uploadAttachments(service, testCaseId, attachments);

            System.out.printf("Update test case scenario with id '%s'\n", testCaseId);
            final TestCaseScenario scenario = new TestCaseScenario()
                    .setSteps(getSteps(result.getSteps(), attachmentsContext));
            final Response<Void> scenarioResponse = service.setScenario(testCaseId, scenario).execute();
            if (!scenarioResponse.isSuccessful()) {
                throw new RuntimeException(scenarioResponse.message());
            }
        }
    }

    private Map<String, TestCaseAttachment> uploadAttachments(final TestCaseService service,
                                                final Long testCaseId,
                                                final List<Attachment> attachments) throws IOException {
        final Map<String, TestCaseAttachment> context = new HashMap<>();
        for (Attachment attachment : attachments) {
            final MultipartBody.Part part = prepareFilePart(attachment);
            final Response<List<TestCaseAttachment>> attachmentResponse = service
                    .addAttachment(testCaseId, Arrays.asList(part)).execute();
            if (!attachmentResponse.isSuccessful()) {
                throw new RuntimeException(attachmentResponse.message());
            }
            context.put(attachment.getSource(), attachmentResponse.body().get(0));
        }
        return context;
    }

    private TestCase getTestCase(final TestResult result, final Long projectId) {
        final TestCase testCase = new TestCase()
                .setName(result.getName())
                .setProjectId(projectId)
                .setFullName(result.getFullName())
                .setDescription(result.getDescription())
                .setAutomated(getLabelValues(result.getLabels(), LABEL_ALLURE_MANUAL).isEmpty());

        testCase.setTags(
                getLabelValues(result.getLabels(), LABEL_TAGS).stream()
                        .map(tag -> new TestTag().setName(tag)).collect(Collectors.toList())
        );
        getLabelValues(result.getLabels(), LABEL_PRECONDITION).stream()
                .findFirst()
                .ifPresent(testCase::setPrecondition);
        getLabelValues(result.getLabels(), LABEL_EXPECTED_RESULT).stream()
                .findFirst()
                .ifPresent(testCase::setExpectedResult);


        return testCase;
    }

    private List<String> getLabelValues(final List<Label> labels, final String name) {
        return labels.stream()
                .filter(l -> name.equals(l.getName()))
                .map(Label::getValue)
                .collect(Collectors.toList());
    }

    private List<Attachment> getAttachments(final List<StepResult> steps) {
        final List<Attachment> attachments = new ArrayList<>();
        if (Objects.nonNull(steps)) {
            steps.forEach(step -> {
                if (Objects.nonNull(step.getAttachments())) {
                    attachments.addAll(step.getAttachments());
                }
                attachments.addAll(getAttachments(step.getSteps()));
            });
        }
        return attachments;
    }

    private List<TestCaseStep> getSteps(final List<StepResult> steps,
                                        final Map<String, TestCaseAttachment> attachmentsContext) {
        final List<TestCaseStep> results = new ArrayList<>();
        if (Objects.nonNull(steps)) {
            steps.forEach(step -> {
                final TestCaseStep result = new TestCaseStep()
                        .setName(step.getName())
                        .setSteps(getSteps(step.getSteps(), attachmentsContext));
                if (Objects.nonNull(step.getAttachments())) {
                    final List<TestCaseAttachment> attachments = step.getAttachments().stream()
                            .map(a -> attachmentsContext.get(a.getSource()))
                            .collect(Collectors.toList());
                    result.setAttachments(attachments);
                }
                results.add(result);
            });
        }
        return results;
    }

    private MultipartBody.Part prepareFilePart(final Attachment attachment) {
        try {
            final byte[] content = Files.readAllBytes(allureResultsDir.resolve(attachment.getSource()));
            final RequestBody requestFile = create(parse(attachment.getType()), content);
            final String originalFileName = attachment.getName();
            return MultipartBody.Part.createFormData("file", originalFileName, requestFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<TestResult> getResults(final Path resultDir) throws IOException {
        final List<Path> resultsPaths = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(resultDir)) {
            paths
                    .filter(Files::isRegularFile)
                    .filter(f -> f.getFileName().toString().endsWith("-result.json"))
                    .forEach(resultsPaths::add);
        }
        final Set<String> fullNames = new HashSet<>();
        final List<TestResult> results = new ArrayList<>();
        final ObjectMapper mapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        for (Path resultPath : resultsPaths) {
            final TestResult result = mapper.readValue(resultPath.toFile(), TestResult.class);
            if (!fullNames.contains(result.getFullName())) {
                fullNames.add(result.getFullName());
                results.add(result);
            }
        }
        return results;
    }

}
