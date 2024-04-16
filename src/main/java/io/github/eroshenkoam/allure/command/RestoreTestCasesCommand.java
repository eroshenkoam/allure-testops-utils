package io.github.eroshenkoam.allure.command;

import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.TestCaseScenarioService;
import io.qameta.allure.ee.client.TestCaseService;
import io.qameta.allure.ee.client.dto.Page;
import io.qameta.allure.ee.client.dto.ScenarioNormalized;
import io.qameta.allure.ee.client.dto.ScenarioStep;
import io.qameta.allure.ee.client.dto.ScenarioStepCreate;
import io.qameta.allure.ee.client.dto.ScenarioStepResponse;
import io.qameta.allure.ee.client.dto.TestCaseAttachment;
import io.qameta.allure.ee.client.dto.TestCasePatch;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@CommandLine.Command(
        name = "restore-testcases", mixinStandardHelpOptions = true,
        description = "Restore test cases in single project"
)
public class RestoreTestCasesCommand extends AbstractBackupRestoreCommand {

    @Override
    public void runUnsafe(ServiceBuilder builder) throws Exception {

        final TestCaseService tcService = builder.create(TestCaseService.class);
        final TestCaseScenarioService tcScenarioService = builder.create(TestCaseScenarioService.class);
        final List<Long> testCasesIds = getTestCases(tcService, allureProjectId, "true");

        for (final Long testCaseId : testCasesIds) {
            restoreTestCase(tcService, tcScenarioService, testCaseId);
        }
    }

    private void restoreTestCase(final TestCaseService tcService,
                                 final TestCaseScenarioService tcScenarioService,
                                 final Long testCaseId) throws IOException {
        System.out.printf("Restore test case with id '%s'\n", testCaseId);

        final Path backupTestCaseFile = getBackupTestCaseFile(testCaseId);
        final TestCaseBackup backup = MAPPER.readValue(backupTestCaseFile.toFile(), TestCaseBackup.class);

        final TestCasePatch patch = backup.getPatch();
        if (Objects.isNull(patch.getFullName())) {
            patch.setFullName(patch.getName());
        }
        executeRequest(tcService.update(testCaseId, patch));
        executeRequest(tcService.setIssues(testCaseId, backup.getIssues()));

        final Map<Long, Long> tcAttachmentContext = restoreTestCaseAttachments(tcScenarioService, backup);
        updateScenarioAttachments(backup.getScenario(), tcAttachmentContext);
        updateTestCaseScenario(tcScenarioService, backup.getScenario(), testCaseId);
    }

    private void updateTestCaseScenario(final TestCaseScenarioService tcScenarioService,
                                        final ScenarioNormalized scenario,
                                        final Long testCaseId) throws IOException {
        executeRequest(tcScenarioService.deleteScenario(testCaseId));
        final ScenarioStep root = scenario.getRoot();
        if (Objects.nonNull(root)) {
            createTestCaseScenarioSteps(tcScenarioService, scenario, root.getChildren(), null, testCaseId);
        }
    }

    private void createTestCaseScenarioSteps(final TestCaseScenarioService tcScenarioService,
                                             final ScenarioNormalized scenario,
                                             final List<Long> stepIds,
                                             final Long parentId,
                                             final Long testCaseId) throws IOException {
        for (Long id : stepIds) {
            final ScenarioStep stepBackup = scenario.getScenarioSteps().get(id);
            if (Objects.nonNull(stepBackup.getAttachmentId())) {
                final ScenarioStepCreate createRequest = new ScenarioStepCreate()
                        .setAttachmentId(stepBackup.getAttachmentId())
                        .setParentId(parentId)
                        .setTestCaseId(testCaseId);
                executeRequest(tcScenarioService.createStep(createRequest, null, null));
            }
            if (Objects.nonNull(stepBackup.getBody())) {
                final ScenarioStepCreate createRequest = new ScenarioStepCreate()
                        .setBody(stepBackup.getBody())
                        .setExpectedResult(stepBackup.getExpectedResult())
                        .setParentId(parentId)
                        .setTestCaseId(testCaseId);
                ScenarioStepResponse createdStep;
                if (Objects.isNull(stepBackup.getExpectedResultId())) {
                    createdStep = executeRequest(tcScenarioService.createStep(createRequest, null, null));
                } else {
                    createdStep = executeRequest(tcScenarioService.createStep(createRequest, null, null, true));
                    final Long expectedResultId = createdStep.getScenario().getScenarioSteps()
                            .get(createdStep.getCreatedStepId()).getExpectedResultId();
                    final List<Long> children = scenario.getScenarioSteps()
                            .get(stepBackup.getExpectedResultId())
                            .getChildren();
                    createTestCaseScenarioSteps(
                            tcScenarioService, scenario, children, expectedResultId, testCaseId
                    );
                }
                final List<Long> children = stepBackup.getChildren();
                if (Objects.nonNull(children)) {
                    createTestCaseScenarioSteps(
                            tcScenarioService, scenario, children, createdStep.getCreatedStepId(), testCaseId
                    );
                }
            }
        }
    }

    private Map<Long, Long> restoreTestCaseAttachments(final TestCaseScenarioService tcScenarioService,
                                                       final TestCaseBackup backup) throws IOException {
        final List<TestCaseAttachment> backupAttachments = backup.getAttachments();
        final Page<TestCaseAttachment> testCaseAttachments = executeRequest(
                tcScenarioService.getAttachments(backup.getId(), 0, 1000)
        );
        for (TestCaseAttachment attachment : testCaseAttachments.getContent()) {
            executeRequest(tcScenarioService.deleteAttachment(attachment.getId()));
        }
        final Map<Long, Long> context = new HashMap<>();
        for (TestCaseAttachment attachment : backupAttachments) {
            final Path attachmentFile = getBackupTestCaseAttachmentFile(backup.getId(), attachment.getId());
            final RequestBody requestBody = RequestBody.create(
                    MediaType.parse(attachment.getContentType()),
                    Files.readAllBytes(attachmentFile)
            );
            final MultipartBody.Part attachmentPart = MultipartBody.Part
                    .createFormData("file", attachment.getName(), requestBody);
            final List<TestCaseAttachment> createdAttachments = executeRequest(
                    tcScenarioService.createAttachment(backup.getId(), List.of(attachmentPart))
            );
            final Long newId = createdAttachments.get(0).getId();
            context.put(attachment.getId(), newId);
        }
        return context;
    }

    private void updateScenarioAttachments(final ScenarioNormalized scenario,
                                           final Map<Long, Long> context) {
        if (Objects.isNull(scenario.getScenarioSteps())) {
            return;
        }
        for (final ScenarioStep step : scenario.getScenarioSteps().values()) {
            if (Objects.nonNull(step.getAttachmentId())) {
                step.setAttachmentId(context.get(step.getAttachmentId()));
            }
        }
    }

}
