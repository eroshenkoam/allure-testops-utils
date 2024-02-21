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
        final TestCaseBackupDto backup = MAPPER.readValue(backupTestCaseFile.toFile(), TestCaseBackupDto.class);

        final Response<TestCase> updateResponse = tcService.update(backup.getTestCase()).execute();
        if (!updateResponse.isSuccessful()) {
            throw new RuntimeException(updateResponse.message());
        }

        final Response<Void> scenarioResponse = tcService
                .setScenario(testCaseId, backup.getScenario()).execute();
        if (!scenarioResponse.isSuccessful()) {
            throw new RuntimeException(scenarioResponse.message());
        }

        final Response<List<Issue>> issueResponse = tcService
                .setIssues(testCaseId, backup.getIssues()).execute();
        if (!issueResponse.isSuccessful()) {
            throw new RuntimeException(issueResponse.message());
        }

        final Response<List<Member>> memberResponse = tcService
                .setMembers(testCaseId, backup.getMembers()).execute();
        if (!memberResponse.isSuccessful()) {
            throw new RuntimeException(memberResponse.message());
        }

        final Response<List<CustomFieldValue>> customFieldsResponse = tcService
                .setCustomFields(testCaseId, backup.getCustomFields()).execute();
        if (!customFieldsResponse.isSuccessful()) {
            throw new RuntimeException(customFieldsResponse.message());
        }
    }

}
