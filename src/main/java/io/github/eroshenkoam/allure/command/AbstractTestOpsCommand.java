package io.github.eroshenkoam.allure.command;

import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.TestCaseService;
import io.qameta.allure.ee.client.dto.Page;
import io.qameta.allure.ee.client.dto.TestCase;
import picocli.CommandLine;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractTestOpsCommand implements Runnable {

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

    @CommandLine.Option(
            names = {"--allure.insecure"},
            description = "Allure TestOps insecure",
            defaultValue = "${env:ALLURE_INSECURE}"
    )
    protected boolean allureInsecure;

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
                .insecure(allureInsecure)
                .authBasic(allureUsername, allurePassword);
    }

    protected List<Long> getTestCases(final TestCaseService service,
                                      final Long projectId,
                                      final String filter) throws IOException {
        final List<Long> testCases = new ArrayList<>();
        Page<TestCase> current = new Page<TestCase>().setNumber(-1);
        do {
            current = executeRequest(
                    service.findByRql(projectId, filter, current.getNumber() + 1, 100)
            );
            for (TestCase item : current.getContent()) {
                testCases.add(item.getId());
            }
        } while (current.getNumber() < current.getTotalPages());
        return testCases;
    }

    protected static <T> T executeRequest(final Call<T> call) throws IOException {
        final Response<T> response = call.execute();
        if (!response.isSuccessful()) {
            throw new RuntimeException(response.errorBody().string());
        }
        return response.body();
    }

}
