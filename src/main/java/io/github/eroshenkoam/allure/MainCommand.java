package io.github.eroshenkoam.allure;

import io.github.eroshenkoam.allure.command.SyncCrowdGroupsCommand;
import picocli.CommandLine;

@CommandLine.Command(
        name = "allure-testops", mixinStandardHelpOptions = true,
        subcommands = {SyncCrowdGroupsCommand.class}
)
public class MainCommand implements Runnable{

    @Override
    public void run() {
    }

}
