package io.github.eroshenkoam.allure.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.SharedStepScenarioService;
import io.qameta.allure.ee.client.SharedStepService;
import io.qameta.allure.ee.client.TestCaseScenarioService;
import io.qameta.allure.ee.client.TestCaseService;
import io.qameta.allure.ee.client.dto.CustomFieldValue;
import io.qameta.allure.ee.client.dto.Issue;
import io.qameta.allure.ee.client.dto.Member;
import io.qameta.allure.ee.client.dto.ScenarioNormalized;
import io.qameta.allure.ee.client.dto.SharedStep;
import io.qameta.allure.ee.client.dto.SharedStepAttachment;
import io.qameta.allure.ee.client.dto.SharedStepUpdate;
import io.qameta.allure.ee.client.dto.TestCase;
import io.qameta.allure.ee.client.dto.TestCaseAttachment;
import io.qameta.allure.ee.client.dto.TestCasePatch;
import okhttp3.ResponseBody;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@CommandLine.Command(
        name = "backup-testcases", mixinStandardHelpOptions = true,
        description = "Backup test cases in single project"
)
public class BackupTestCasesCommand extends AbstractBackupRestoreCommand {

    private final static ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void runUnsafe(ServiceBuilder builder) throws Exception {

        final SharedStepService ssService = builder.create(SharedStepService.class);
        final SharedStepScenarioService ssScenarioService = builder.create(SharedStepScenarioService.class);
        final List<Long> sharedStepIds = getSharedSteps(ssService, allureProjectId);

        for (final Long sharedStepId : sharedStepIds) {
            backupSharedStep(ssService, ssScenarioService, sharedStepId);
        }

        final TestCaseService tcService = builder.create(TestCaseService.class);
        final TestCaseScenarioService tcScenarioService = builder.create(TestCaseScenarioService.class);

        final List<Long> testCasesIds = getTestCases(tcService, allureProjectId, "true");
        for (final Long testCaseId : testCasesIds) {
            backupTestCase(tcService, tcScenarioService, testCaseId);
        }
    }

    private void backupSharedStep(final SharedStepService ssService,
                                  final SharedStepScenarioService ssScenarioService,
                                  final Long sharedStepId) throws IOException {
        final SharedStep sharedStep = executeRequest(ssService.findOne(sharedStepId));
        final ScenarioNormalized scenario = executeRequest(ssScenarioService.getScenario(sharedStepId));

        final SharedStepUpdate patch = new SharedStepUpdate()
                .setName(sharedStep.getName());

        final List<SharedStepAttachment> attachments = executeRequest(
                ssScenarioService.getAttachments(sharedStepId, 0, 1000)
        ).getContent();

        final SharedStepBackup backup = new SharedStepBackup()
                .setId(sharedStepId)
                .setPatch(patch)
                .setScenario(scenario)
                .setAttachments(attachments);

        final Path sharedStepFile = getBackupSharedStepFile(sharedStepId);
        MAPPER.writeValue(sharedStepFile.toFile(), backup);

        for(final SharedStepAttachment attachment: attachments) {
            final ResponseBody attachmentContent = executeRequest(
                    ssScenarioService.getAttachmentContent(attachment.getId())
            );
            final Path attachmentContentPath = getBackupSharedStepAttachmentFile(sharedStepId, attachment.getId());
            Files.write(attachmentContentPath.toAbsolutePath(), attachmentContent.bytes());
        }
    }

    private void backupTestCase(final TestCaseService tcService,
                                final TestCaseScenarioService tcScenarioService,
                                final Long testCaseId) throws IOException {
        System.out.printf("Backup test case with id '%s'\n", testCaseId);

        final TestCase testCase = executeRequest(tcService.findById(testCaseId));
        final ScenarioNormalized scenario = executeRequest(tcScenarioService.getScenario(testCaseId));
        final List<Issue> issues = executeRequest(tcService.getIssues(testCaseId));
        final List<Member> members = executeRequest(tcService.getMembers(testCaseId));
        final List<CustomFieldValue> customFields = executeRequest(tcService.getCustomFields(testCaseId));

        final TestCasePatch patch = new TestCasePatch()
                .setName(testCase.getName())
                .setFullName(testCase.getFullName())
                .setStatusId(testCase.getStatus().getId())
                .setWorkflowId(testCase.getWorkflow().getId())
                .setDescription(testCase.getDescription())
                .setPrecondition(testCase.getPrecondition())
                .setExpectedResult(testCase.getExpectedResult())
                .setDeleted(testCase.getDeleted())
                .setExternal(testCase.getExternal())
                .setAutomated(testCase.getAutomated())
                .setTags(testCase.getTags())
                .setLinks(testCase.getLinks())
                .setMembers(members)
                .setCustomFields(customFields);

        final List<TestCaseAttachment> attachments = executeRequest(
                tcService.getAttachments(testCaseId, 0, 1000)
        ).getContent();

        final TestCaseBackup backup = new TestCaseBackup()
                .setId(testCaseId)
                .setPatch(patch)
                .setIssues(issues)
                .setAttachments(attachments)
                .setScenario(scenario);

        final Path testCasePath = getBackupTestCaseFile(testCaseId);
        MAPPER.writeValue(testCasePath.toFile(), backup);

        for(final TestCaseAttachment attachment: attachments) {
            final ResponseBody attachmentContent = executeRequest(tcService.getAttachmentContent(attachment.getId()));
            final Path attachmentContentPath = getBackupTestCaseAttachmentFile(testCaseId, attachment.getId());
            Files.write(attachmentContentPath.toAbsolutePath(), attachmentContent.bytes());
        }

    }

}
