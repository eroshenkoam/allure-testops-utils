package io.github.eroshenkoam.allure.command;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
            defaultValue = "${env:BACKUP_PATH}"
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

    protected Path getBackupAttachmentsFile(final Long testCaseId) throws IOException {
        return getBackupTestCaseDir(testCaseId)
                .resolve("attachments.json");
    }

    protected Path getBackupAttachmentContentFile(final Long testCaseId, final Long attachmentId) throws IOException {
        return getBackupTestCaseDir(testCaseId)
                .resolve(String.format("attachment-%s", attachmentId));
    }
}
