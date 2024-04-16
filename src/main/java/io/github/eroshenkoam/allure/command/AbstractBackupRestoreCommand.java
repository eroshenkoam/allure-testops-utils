package io.github.eroshenkoam.allure.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.ee.client.dto.Issue;
import io.qameta.allure.ee.client.dto.ScenarioNormalized;
import io.qameta.allure.ee.client.dto.SharedStepAttachment;
import io.qameta.allure.ee.client.dto.SharedStepUpdate;
import io.qameta.allure.ee.client.dto.TestCaseAttachment;
import io.qameta.allure.ee.client.dto.TestCasePatch;
import lombok.Data;
import lombok.experimental.Accessors;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public abstract class AbstractBackupRestoreCommand extends AbstractTestOpsCommand {

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
            defaultValue = "${env:BACKUP_PATH}",
            required = true
    )
    protected String backupPath;

    protected final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    protected Path getBackupDir() throws IOException {
        final Path backupDir = Paths.get(this.backupPath);
        if (Files.notExists(backupDir)) {
            Files.createDirectories(backupDir);
        }
        return backupDir;
    }

    protected Path getBackupTestCaseDir(final Long testCaseId) throws IOException {
        final Path testCaseDir = getBackupDir()
                .resolve(String.format("tc-%s", testCaseId));
        Files.createDirectories(testCaseDir);
        return testCaseDir;
    }

    protected Path getBackupTestCaseFile(final Long testCaseId) throws IOException {
        return getBackupTestCaseDir(testCaseId)
                .resolve("testcase.json");
    }

    protected Path getBackupTestCaseAttachmentFile(final Long testCaseId, final Long attachmentId) throws IOException {
        return getBackupTestCaseDir(testCaseId)
                .resolve(String.format("attachment-%s", attachmentId));
    }

    protected Path getBackupSharedStepDir(final Long sharedStepId) throws IOException {
        final Path sharedStepDir = getBackupDir()
                .resolve(String.format("ss-%s", sharedStepId));
        Files.createDirectories(sharedStepDir);
        return sharedStepDir;
    }

    protected Path getBackupSharedStepFile(final Long sharedStepId) throws IOException {
        return getBackupSharedStepDir(sharedStepId)
                .resolve("sharedstep.json");
    }

    protected Path getBackupSharedStepAttachmentFile(
            final Long sharedStepId,
            final Long attachmentId) throws IOException {
        return getBackupSharedStepDir(sharedStepId)
                .resolve(String.format("attachment-%s", attachmentId));
    }


    @Data
    @Accessors(chain = true)
    public static class TestCaseBackup {

        private Long id;
        private TestCasePatch patch;
        private List<Issue> issues;
        private ScenarioNormalized scenario;
        private List<TestCaseAttachment> attachments;

    }

    @Data
    @Accessors(chain = true)
    public static class SharedStepBackup {

        private Long id;
        private SharedStepUpdate patch;
        private ScenarioNormalized scenario;
        private List<SharedStepAttachment> attachments;

    }

}
