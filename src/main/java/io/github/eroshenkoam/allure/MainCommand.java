package io.github.eroshenkoam.allure;

import io.github.eroshenkoam.allure.command.CrowdSyncGroupsCommand;
import picocli.CommandLine;

@CommandLine.Command(
        name = "allure-testops-utils", mixinStandardHelpOptions = true,
        subcommands = {CrowdSyncGroupsCommand.class}
)
public class MainCommand implements Runnable{

    @Override
    public void run() {
    }

}
