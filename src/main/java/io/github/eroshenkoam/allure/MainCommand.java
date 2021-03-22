package io.github.eroshenkoam.allure;

import io.github.eroshenkoam.allure.sync.SyncCommand;
import picocli.CommandLine;

@CommandLine.Command(
        name = "allure-testops-crowd", mixinStandardHelpOptions = true,
        subcommands = {SyncCommand.class}
)
public class MainCommand implements Runnable{

    @Override
    public void run() {
    }

}
