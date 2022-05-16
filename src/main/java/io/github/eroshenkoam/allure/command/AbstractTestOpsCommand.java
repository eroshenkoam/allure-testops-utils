package io.github.eroshenkoam.allure.command;

import io.qameta.allure.ee.client.ServiceBuilder;
import picocli.CommandLine;

public abstract class AbstractTestOpsCommand implements Runnable{

    @CommandLine.Option(
            names = {"--allure.endpoint"},
            description = "Allure TestOps endpoint",
            defaultValue = "${env:ALLURE_ENDPOINT}"
    )
    protected String allureEndpoint;

    @CommandLine.Option(
            names = {"--allure.username"},
            description = "Allure TestOps username",
            defaultValue = "${env:ALLURE_USERNAME}"
    )
    protected String allureUsername;

    @CommandLine.Option(
            names = {"--allure.password"},
            description = "Allure TestOps password",
            defaultValue = "${env:ALLURE_PASSWORD}"
    )
    protected String allurePassword;

    public abstract void runUnsafe(final ServiceBuilder builder) throws Exception;

    @Override
    public void run() {
        try {
            final ServiceBuilder builder = getAllureServiceBuilder();
            runUnsafe(builder);
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    protected ServiceBuilder getAllureServiceBuilder() {
        return new ServiceBuilder(allureEndpoint)
                .authBasic(allureUsername, allurePassword);
    }

}
