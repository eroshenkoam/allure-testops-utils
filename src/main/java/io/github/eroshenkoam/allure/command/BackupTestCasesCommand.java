package io.github.eroshenkoam.allure.command;

import io.github.eroshenkoam.allure.model.TestCaseBackupDto;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.TestCaseService;
import io.qameta.allure.ee.client.dto.CustomFieldValue;
import io.qameta.allure.ee.client.dto.Issue;
import io.qameta.allure.ee.client.dto.Member;
import io.qameta.allure.ee.client.dto.TestCase;
import io.qameta.allure.ee.client.dto.TestCaseRelation;
import io.qameta.allure.ee.client.dto.TestCaseScenario;
import io.qameta.allure.ee.client.dto.TestTag;
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
public class BackupTestCasesCommand extends AbstractTestOpsCommand {

    @CommandLine.Option(
            names = {"--allure.project.id"},
            description = "Allure TestOps project id",
            defaultValue = "${env:ALLURE_PROJECT_ID}",
            required = true
    )
    protected Long allureProjectId;

    @CommandLine.Option(
            names = {"--backup.path"},
            description = "Backup path",
            defaultValue = "${env:BACKUP_PATH}"
    )
    protected String backupPath;


    @Override
    public void runUnsafe(ServiceBuilder builder) throws Exception {

        final TestCaseService tcService = builder.create(TestCaseService.class);
        final List<Long> testCasesIds = getTestCases(tcService, allureProjectId, "true");

        final Path backupDir = Paths.get(this.backupPath);
        if (Files.notExists(backupDir)) {
            Files.createDirectories(backupDir);
        }

        for (final Long testCaseId : testCasesIds) {
            backupTestCase(tcService, backupDir, testCaseId);
        }
    }

    private void backupTestCase(final TestCaseService tcService,
                                final Path backupDir,
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

        final Response<List<TestTag>> tagResponse = tcService.getTags(testCaseId).execute();
        if (!tagResponse.isSuccessful()) {
            throw new RuntimeException(tagResponse.message());
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

        final Response<List<TestCaseRelation>> relationsResponse = tcService.getRelations(testCaseId).execute();
        if (!relationsResponse.isSuccessful()) {
            throw new RuntimeException(relationsResponse.message());
        }

        final TestCaseBackupDto testCaseBackup = new TestCaseBackupDto()
                .setTags(tagResponse.body())
                .setIssues(issueResponse.body())
                .setMembers(memberResponse.body())
                .setCustomFields(customFieldsResponse.body())
                .setRelations(relationsResponse.body())
                .setTestCase(testCaseResponse.body())
                .setScenario(scenarioResponse.body());
    }

}
