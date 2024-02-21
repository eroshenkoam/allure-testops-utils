package io.github.eroshenkoam.allure.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eroshenkoam.allure.model.TestCaseBackupDto;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.TestCaseService;
import io.qameta.allure.ee.client.dto.CustomFieldValue;
import io.qameta.allure.ee.client.dto.Issue;
import io.qameta.allure.ee.client.dto.Member;
import io.qameta.allure.ee.client.dto.TestCase;
import io.qameta.allure.ee.client.dto.TestCaseScenario;
import picocli.CommandLine;
import retrofit2.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

        final Response<TestCase> testCaseResponse = tcService.findById(testCaseId).execute();
        if (!testCaseResponse.isSuccessful()) {
            throw new RuntimeException(testCaseResponse.message());
        }

        final Response<TestCaseScenario> scenarioResponse = tcService.getScenario(testCaseId).execute();
        if (!scenarioResponse.isSuccessful()) {
            throw new RuntimeException(scenarioResponse.message());
        }

        final Response<List<Issue>> issueResponse = tcService.getIssues(testCaseId).execute();
        if (!issueResponse.isSuccessful()) {
            throw new RuntimeException(issueResponse.message());
        }

        final Response<List<Member>> memberResponse = tcService.getMembers(testCaseId).execute();
        if (!memberResponse.isSuccessful()) {
            throw new RuntimeException(memberResponse.message());
        }

        final Response<List<CustomFieldValue>> customFieldsResponse = tcService.getCustomFields(testCaseId).execute();
        if (!customFieldsResponse.isSuccessful()) {
            throw new RuntimeException(customFieldsResponse.message());
        }

        final TestCaseBackupDto testCaseBackup = new TestCaseBackupDto()
                .setIssues(issueResponse.body())
                .setMembers(memberResponse.body())
                .setCustomFields(customFieldsResponse.body())
                .setTestCase(testCaseResponse.body())
                .setScenario(scenarioResponse.body());

        final Path testCasePath = getBackupTestCaseFile(testCaseId);
        MAPPER.writeValue(testCasePath.toFile(), testCaseBackup);
    }

}
