package io.github.eroshenkoam.allure;

import io.github.eroshenkoam.allure.command.CrowdSyncGroupsCommand;
import io.github.eroshenkoam.allure.command.LaunchCleanCommand;
import io.github.eroshenkoam.allure.command.LdapSyncGroupsCommand;
import picocli.CommandLine;

@CommandLine.Command(
        name = "allure-testops-utils", mixinStandardHelpOptions = true,
        subcommands = {
                CrowdSyncGroupsCommand.class,
                LdapSyncGroupsCommand.class,
                LaunchCleanCommand.class
        }
)
public class MainCommand implements Runnable{

    @Override
    public void run() {
    }

}
