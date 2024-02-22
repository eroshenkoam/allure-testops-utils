package io.github.eroshenkoam.allure.command;

import com.fasterxml.jackson.core.type.TypeReference;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.TestCaseService;
import io.qameta.allure.ee.client.dto.Page;
import io.qameta.allure.ee.client.dto.TestCaseAttachment;
import io.qameta.allure.ee.client.dto.TestCasePatch;
import io.qameta.allure.ee.client.dto.TestCaseScenario;
import io.qameta.allure.ee.client.dto.TestCaseStep;
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
        final List<Long> testCasesIds = getTestCases(tcService, allureProjectId, "true");

        for (final Long testCaseId : testCasesIds) {
            restoreTestCase(tcService, testCaseId);
        }
    }

    private void restoreTestCase(final TestCaseService tcService,
                                 final Long testCaseId) throws IOException {
        System.out.printf("Restore test case with id '%s'\n", testCaseId);


        final Path backupTestCaseFile = getBackupTestCaseFile(testCaseId);
        final TestCasePatch backup = MAPPER.readValue(backupTestCaseFile.toFile(), TestCasePatch.class);

        final Map<Long, Long> attachmentContext = restoreAttachments(tcService, testCaseId);
        updateScenarioAttachments(backup.getScenario(), attachmentContext);

        executeRequest(tcService.update(testCaseId, backup));
    }

    private Map<Long, Long> restoreAttachments(final TestCaseService tcService,
                                               final Long testCaseId) throws IOException {
        final Path attachmentsFile = getBackupAttachmentsFile(testCaseId);
        // @formatter:off
        final List<TestCaseAttachment> backupAttachments = MAPPER
                .readValue(attachmentsFile.toFile(), new TypeReference<List<TestCaseAttachment>>(){});
        // @formatter:on
        final Page<TestCaseAttachment> testCaseAttachments = executeRequest(
                tcService.getAttachments(testCaseId, 0, 1000)
        );
        for (TestCaseAttachment attachment: testCaseAttachments.getContent()) {
            executeRequest(tcService.deleteAttachment(attachment.getId()));
        }
        final Map<Long, Long> context = new HashMap<>();
        for (TestCaseAttachment attachment: backupAttachments) {
            final Path attachmentFile = getBackupAttachmentContentFile(testCaseId, attachment.getId());
            final RequestBody requestBody = RequestBody.create(
                    MediaType.parse(attachment.getContentType()),
                    Files.readAllBytes(attachmentFile)
            );
            final MultipartBody.Part attachmentPart = MultipartBody.Part
                    .createFormData("file", attachment.getName(), requestBody);
            final List<TestCaseAttachment> createdAttachments = executeRequest(
                    tcService.addAttachment(testCaseId, List.of(attachmentPart))
            );
            final Long newId = createdAttachments.get(0).getId();
            context.put(attachment.getId(), newId);
        }
        return context;
    }

    private void updateScenarioAttachments(final TestCaseScenario scenario,
                                           final Map<Long, Long> context) {
        updateStepAttachments(scenario.getSteps(), context);
    }

    private void updateStepAttachments(final List<TestCaseStep> steps,
                                       final Map<Long, Long> context) {
        if (Objects.isNull(steps)) {
            return;
        }
        for (final TestCaseStep step: steps) {
            if (Objects.nonNull(step.getAttachments())) {
                for (final TestCaseAttachment attachment: step.getAttachments()) {
                    attachment.setId(context.get(attachment.getId()));
                }
            }
            updateStepAttachments(step.getSteps(), context);
        }
    }

}
