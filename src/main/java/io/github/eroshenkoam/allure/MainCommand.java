package io.github.eroshenkoam.allure;

import io.github.eroshenkoam.allure.command.*;
import io.github.eroshenkoam.allure.retrofit.GitlabInterceptor;
import picocli.CommandLine;

@CommandLine.Command(
        name = "allure-testops-utils", mixinStandardHelpOptions = true,
        subcommands = {
                CrowdSyncGroupsCommand.class,
                GitlabSyncGroupsCommand.class,
                LdapSyncGroupsCommand.class,
                LdapDisableUsersCommand.class,
                FileDisableUsersCommand.class,
                LaunchCleanCommand.class,
                ExportTestCasesCommand.class,
                MigrateExpectedResultsCommand.class,
                RollbackTestCasesCommand.class
        }
)
public class MainCommand implements Runnable{

    @Override
    public void run() {
    }

}
