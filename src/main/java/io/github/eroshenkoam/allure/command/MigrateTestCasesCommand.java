package io.github.eroshenkoam.allure.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.SharedStepScenarioService;
import io.qameta.allure.ee.client.SharedStepService;
import io.qameta.allure.ee.client.TestCaseScenarioService;
import io.qameta.allure.ee.client.TestCaseService;
import io.qameta.allure.ee.client.dto.Page;
import io.qameta.allure.ee.client.dto.ScenarioAttachment;
import io.qameta.allure.ee.client.dto.ScenarioNormalized;
import io.qameta.allure.ee.client.dto.ScenarioStep;
import io.qameta.allure.ee.client.dto.ScenarioStepCreate;
import io.qameta.allure.ee.client.dto.ScenarioStepResponse;
import io.qameta.allure.ee.client.dto.ScenarioStepUpdate;
import io.qameta.allure.ee.client.dto.SharedStep;
import io.qameta.allure.ee.client.dto.SharedStepAttachment;
import io.qameta.allure.ee.client.dto.SharedStepCreate;
import io.qameta.allure.ee.client.dto.TestCase;
import io.qameta.allure.ee.client.dto.TestCaseAttachment;
import io.qameta.allure.model.Attachment;
import lombok.Data;
import lombok.experimental.Accessors;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import org.springframework.util.NumberUtils;
import org.springframework.util.StreamUtils;
import picocli.CommandLine;
import retrofit2.Response;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

@CommandLine.Command(
        name = "migrate-testcases",
        mixinStandardHelpOptions = true,
        description = "Migrate test cases from old style"
)
public class MigrateTestCasesCommand extends AbstractTestOpsCommand {

    private static final String RQL_TEST_CASE = "not layer in [\"Shared Steps\", \"Shared\", \"Шаги\", \"Шаг\"]";
    private static final String RQL_SHARED_STEP = "layer in [\"Shared Steps\", \"Shared\", \"Шаги\", \"Шаг\"]";

    private static final ObjectMapper MAPPER = new ObjectMapper().disable(FAIL_ON_UNKNOWN_PROPERTIES);


    @CommandLine.Option(
            names = {"--allure.project.id"},
            description = "Allure TestOps project id",
            defaultValue = "${env:ALLURE_PROJECT_ID}",
            required = true
    )
    protected Long allureProjectId;

    @Override
    public void runUnsafe(final ServiceBuilder builder) throws Exception {
        final TestCaseService tcService = builder.create(TestCaseService.class);
        final TestCaseScenarioService tcScenarioService = builder.create(TestCaseScenarioService.class);
        final SharedStepService ssService = builder.create(SharedStepService.class);
        final SharedStepScenarioService ssScenarioService = builder.create(SharedStepScenarioService.class);

        migrateProjectTestCases(tcService, tcScenarioService, allureProjectId);
        final Map<Long, Long> sharedSteps = migrateProjectSharedSteps(
                tcService, ssService, tcScenarioService, ssScenarioService, allureProjectId
        );
        migrateProjectCustomSteps(tcService, tcScenarioService, sharedSteps, allureProjectId);
    }

    private void migrateProjectTestCases(final TestCaseService tcService,
                                         final TestCaseScenarioService tcScenarioService,
                                         final Long projectId) throws Exception {
        final Map<Long, String> testCasesMap = getAllTestCasesIds(tcService, projectId, "true");
        final Set<Long> testCaseIds = testCasesMap.keySet();
        System.out.printf("Found information about %d test cases\n", testCasesMap.size());
        for (Long testCaseId : testCaseIds) {
            System.out.printf("Migrating test case scenario with id %s\n", testCaseId);
            final Response<ResponseBody> migrateResponse = tcScenarioService.migrateScenario(testCaseId).execute();
            if (!migrateResponse.isSuccessful()) {
                throw new RuntimeException(migrateResponse.message());
            }
        }
    }

    private void migrateProjectCustomSteps(final TestCaseService tcService,
                                           final TestCaseScenarioService tcScenarioService,
                                           final Map<Long, Long> sharedSteps,
                                           final Long projectId) throws Exception {
        final Map<Long, String> testCasesMap = getAllTestCasesIds(tcService, projectId, RQL_TEST_CASE);
        final Set<Long> testCaseIds = testCasesMap.keySet();
        System.out.printf("Found information about %d test cases\n", testCasesMap.size());
        for (Long testCaseId : testCaseIds) {
            System.out.printf("Migrating test case with id %s\n", testCaseId);
            migrateTestCase(tcScenarioService, testCaseId, sharedSteps);
        }
    }

    private Map<Long, Long> migrateProjectSharedSteps(final TestCaseService tcService,
                                                      final SharedStepService ssService,
                                                      final TestCaseScenarioService tcScenarioService,
                                                      final SharedStepScenarioService ssScenarioService,
                                                      final Long projectId) throws Exception {
        final Map<Long, Long> steps = new HashMap<>();

        final Map<Long, String> sharedStepsMap = getAllSharedSteps(ssService, projectId);
        final Map<Long, String> testCasesMap = getAllTestCasesIds(tcService, projectId, RQL_SHARED_STEP);

        System.out.printf("Found information about %d shared steps\n", testCasesMap.size());
        for (Map.Entry<Long, String> entry : testCasesMap.entrySet()) {
            final Long testCaseId = entry.getKey();
            final Optional<Map.Entry<Long, String>> existing = sharedStepsMap.entrySet().stream()
                    .filter(e -> e.getValue().endsWith(String.format("#%s#", testCaseId)))
                    .findFirst();
            if (existing.isPresent()) {
                final Long sharedStepId = existing.get().getKey();
                steps.put(testCaseId, sharedStepId);
                copySharedStepScenario(tcScenarioService, ssScenarioService, testCaseId, sharedStepId);
                System.out.printf("Found existing shared step %s = %s\n", testCaseId, sharedStepId);
            } else {
                System.out.printf("Migrating shared step with id %s\n", testCaseId);
                final Long sharedStepId = createSharedStep(tcService, ssService, testCaseId, projectId);
                copySharedStepScenario(tcScenarioService, ssScenarioService, testCaseId, sharedStepId);
                System.out.printf("Shared step successfully migrated with id %s\n", sharedStepId);
                steps.put(testCaseId, sharedStepId);
            }
        }

        return steps;
    }

    private void migrateTestCase(final TestCaseScenarioService scenarioService,
                                 final Long testCaseId,
                                 final Map<Long, Long> sharedSteps) throws Exception {
        final Response<ScenarioNormalized> scenarioResponse = scenarioService.getScenario(testCaseId).execute();
        if (!scenarioResponse.isSuccessful()) {
            throw new RuntimeException(scenarioResponse.message());
        }
        final ScenarioNormalized scenario = scenarioResponse.body();
        for (Map.Entry<Long, ScenarioAttachment> entry : scenario.getAttachments().entrySet()) {
            if (entry.getValue().getContentType().equals("shared/json")) {
                final String name = entry.getValue().getName().replaceFirst("shared-step-", "");
                final Long customStepId = Long.parseLong(name);
                final Long stepId = scenario.getScenarioSteps().entrySet().stream()
                        .filter(e -> entry.getKey().equals(e.getValue().getAttachmentId()))
                        .map(Map.Entry::getKey)
                        .findAny()
                        .orElseThrow();
                final ScenarioStepUpdate update = new ScenarioStepUpdate()
                        .setBody(null)
                        .setExpectedResult(null)
                        .setAttachmentId(null)
                        .setSharedStepId(sharedSteps.get(customStepId));
                final Response<ScenarioNormalized> updateResponse = scenarioService
                        .updateStep(stepId, update).execute();
                if (!updateResponse.isSuccessful()) {
                    throw new RuntimeException(updateResponse.errorBody().string());
                }
            }
        }
    }

    private Long createSharedStep(final TestCaseService testCaseService,
                                  final SharedStepService sharedStepService,
                                  final Long testCaseId,
                                  final Long projectId) throws Exception {
        final Response<TestCase> testCaseResponse = testCaseService.findById(testCaseId).execute();
        if (!testCaseResponse.isSuccessful()) {
            throw new RuntimeException(testCaseResponse.message());
        }
        final TestCase testCase = testCaseResponse.body();
        final SharedStepCreate createRequest = new SharedStepCreate()
                .setName(String.format("%s #%s#", testCase.getName(), testCase.getId()))
                .setProjectId(projectId);
        final Response<SharedStep> createResponse = sharedStepService.createStep(createRequest).execute();
        if (!createResponse.isSuccessful()) {
            throw new RuntimeException(createResponse.message());
        }
        return createResponse.body().getId();

    }

    private void copySharedStepScenario(final TestCaseScenarioService tcScenarioService,
                                        final SharedStepScenarioService ssScenarioService,
                                        final Long testCaseId,
                                        final Long sharedStepId) throws Exception {
        final Response<ScenarioNormalized> tcScenarioResponse = tcScenarioService.getScenario(testCaseId).execute();
        if (!tcScenarioResponse.isSuccessful()) {
            throw new RuntimeException(tcScenarioResponse.message());
        }
        final ScenarioNormalized scenario = tcScenarioResponse.body();
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
        final Response<ScenarioNormalized> scenarioResponse = ssScenarioService.getScenario(sharedStepId).execute();
        if (!scenarioResponse.isSuccessful()) {
            throw new RuntimeException(scenarioResponse.message());
        }
        final ScenarioNormalized scenario = scenarioResponse.body();
        if (Objects.nonNull(scenario.getRoot().getChildren())) {
            for (Long stepId : scenario.getRoot().getChildren()) {
                final Response<ScenarioNormalized> deleteResponse = ssScenarioService.deleteStep(stepId).execute();
                if (!deleteResponse.isSuccessful()) {
                    throw new RuntimeException(deleteResponse.message());
                }
            }
        }
        final Response<Page<SharedStepAttachment>> attachmentsResponse = ssScenarioService
                .getAttachments(sharedStepId, 0, 1000).execute();
        if (!attachmentsResponse.isSuccessful()) {
            throw new RuntimeException(attachmentsResponse.message());
        }
        if (Objects.nonNull(attachmentsResponse.body())) {
            final List<Long> attachments = attachmentsResponse.body().getContent().stream()
                    .map(SharedStepAttachment::getId)
                    .collect(Collectors.toList());
            for (Long attachmentId : attachments) {
                final Response<Void> deleteResponse = ssScenarioService.deleteAttachment(attachmentId).execute();
                if (!deleteResponse.isSuccessful()) {
                    throw new RuntimeException(deleteResponse.message());
                }
            }
        }
    }

    private Map<Long, Long> copySharedStepAttachments(final TestCaseScenarioService tcScenarioService,
                                                      final SharedStepScenarioService ssScenarioService,
                                                      final Long testCaseId,
                                                      final Long sharedStepId) throws Exception {
        final Map<Long, Long> result = new HashMap<>();

        final Response<Page<TestCaseAttachment>> attachmentsResponse = tcScenarioService
                .getAttachments(testCaseId, 0, 1000).execute();
        if (!attachmentsResponse.isSuccessful()) {
            throw new RuntimeException(attachmentsResponse.message());
        }
        final List<TestCaseAttachment> attachments = attachmentsResponse.body().getContent();
        for (TestCaseAttachment attachment : attachments) {
            final Response<ResponseBody> contentResposne = tcScenarioService
                    .getAttachmentContent(attachment.getId()).execute();
            if (contentResposne.isSuccessful()) {
                final RequestBody requestBody = RequestBody.create(
                        MediaType.parse(attachment.getContentType()),
                        contentResposne.body().bytes()
                );
                final MultipartBody.Part create = MultipartBody.Part
                        .createFormData("file", attachment.getName(), requestBody);
                final Response<List<SharedStepAttachment>> createResponse = ssScenarioService
                        .createAttachment(sharedStepId, Arrays.asList(create)).execute();
                if (createResponse.isSuccessful()) {
                    result.put(attachment.getId(), createResponse.body().get(0).getId());
                }
            }
        }
        return result;
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
            if (Objects.nonNull(currentStep.getAttachmentId())) {
                if (attachmentContext.containsKey(currentStep.getAttachmentId())) {
                    createRequest = createRequest
                            .setAttachmentId(attachmentContext.get(currentStep.getAttachmentId()));
                } else {
                    createRequest = createRequest
                            .setBody(String.format("Missing attachment with id %s", currentStep.getAttachmentId()));
                }
            } else {
                createRequest = createRequest.setBody(currentStep.getBody())
                        .setExpectedResult(currentStep.getExpectedResult());
            }
            final Response<ScenarioStepResponse> createResponse = ssScenarioService
                    .createStep(createRequest, null, createdId).execute();
            if (!createResponse.isSuccessful()) {
                throw new RuntimeException(createResponse.errorBody().string());
            }
            createdId = createResponse.body().getCreatedStepId();
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

    private Map<Long, String> getAllTestCasesIds(final TestCaseService service,
                                                 final Long projectId,
                                                 final String filter) throws IOException {
        final Map<Long, String> result = new HashMap<>();
        Page<TestCase> current = new Page<TestCase>().setNumber(-1);
        do {
            final Response<Page<TestCase>> response = service
                    .findByRql(projectId, filter, current.getNumber() + 1, 100).execute();
            if (!response.isSuccessful()) {
                throw new RuntimeException("Can not find test cases: " + response.message());
            }
            current = response.body();
            for (TestCase item : current.getContent()) {
                result.put(item.getId(), item.getName());
            }
        } while (current.getNumber() < current.getTotalPages());
        return result;
    }

    private Map<Long, String> getAllSharedSteps(final SharedStepService service,
                                                final Long projectId) throws IOException {
        final Map<Long, String> result = new HashMap<>();
        Page<SharedStep> current = new Page<SharedStep>().setNumber(-1);
        do {
            final Response<Page<SharedStep>> response = service
                    .findAll(projectId, null, false, current.getNumber() + 1, 100).execute();
            if (!response.isSuccessful()) {
                throw new RuntimeException("Can not find test cases: " + response.message());
            }
            current = response.body();
            for (SharedStep item : current.getContent()) {
                result.put(item.getId(), item.getName());
            }
        } while (current.getNumber() < current.getTotalPages());
        return result;
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
}
