package io.github.eroshenkoam.allure;

import io.github.eroshenkoam.allure.command.CleanIntegrationsCommand;
import io.github.eroshenkoam.allure.command.CrowdSyncGroupsCommand;
import io.github.eroshenkoam.allure.command.ExportTestCasesCommand;
import io.github.eroshenkoam.allure.command.GitlabSyncGroupsCommand;
import io.github.eroshenkoam.allure.command.LaunchCleanCommand;
import io.github.eroshenkoam.allure.command.LdapSyncGroupsCommand;
import io.github.eroshenkoam.allure.command.RollbackTestCasesCommand;
import io.github.eroshenkoam.allure.retrofit.GitlabInterceptor;
import picocli.CommandLine;

@CommandLine.Command(
        name = "allure-testops-utils", mixinStandardHelpOptions = true,
        subcommands = {
                CrowdSyncGroupsCommand.class,
                GitlabSyncGroupsCommand.class,
                LdapSyncGroupsCommand.class,
                LaunchCleanCommand.class,
                ExportTestCasesCommand.class,
                RollbackTestCasesCommand.class,
                CleanIntegrationsCommand.class
        }
)
public class MainCommand implements Runnable{

    @Override
    public void run() {
    }

}
