package io.github.eroshenkoam.allure.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.TestCaseService;
import io.qameta.allure.ee.client.dto.CustomFieldValue;
import io.qameta.allure.ee.client.dto.Member;
import io.qameta.allure.ee.client.dto.Page;
import io.qameta.allure.ee.client.dto.TestCase;
import io.qameta.allure.ee.client.dto.TestCaseAttachment;
import io.qameta.allure.ee.client.dto.TestCasePatch;
import io.qameta.allure.ee.client.dto.TestCaseScenario;
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

        final TestCaseService tcService = builder.create(TestCaseService.class);
        final List<Long> testCasesIds = getTestCases(tcService, allureProjectId, "true");

        for (final Long testCaseId : testCasesIds) {
            backupTestCase(tcService, testCaseId);
        }
    }

    private void backupTestCase(final TestCaseService tcService,
                                final Long testCaseId) throws IOException {
        System.out.printf("Backup test case with id '%s'\n", testCaseId);

        final TestCase testCase = executeRequest(tcService.findById(testCaseId));
        final TestCaseScenario scenario = executeRequest(tcService.getScenario(testCaseId));
        final List<Member> members = executeRequest(tcService.getMembers(testCaseId));
        final List<CustomFieldValue> customFields = executeRequest(tcService.getCustomFields(testCaseId));

        final TestCasePatch backup = new TestCasePatch()
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
                .setCustomFields(customFields)
                .setScenario(scenario);

        final Path testCasePath = getBackupTestCaseFile(testCaseId);
        MAPPER.writeValue(testCasePath.toFile(), backup);

        final Page<TestCaseAttachment> attachments = executeRequest(tcService.getAttachments(testCaseId, 0, 1000));
        final Path attachmentsPath = getBackupAttachmentsFile(testCaseId);
        MAPPER.writeValue(attachmentsPath.toFile(), attachments);

        for(final TestCaseAttachment attachment: attachments.getContent()) {
            final ResponseBody attachmentContent = executeRequest(tcService.getAttachmentContent(attachment.getId()));
            final Path attachmentContentPath = getBackupAttachmentContentFile(testCaseId, attachment.getId());
            Files.write(attachmentContentPath.toAbsolutePath(), attachmentContent.bytes());
        }
    }

}
