package io.github.eroshenkoam.allure.command;

import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.SharedStepService;
import io.qameta.allure.ee.client.TestCaseService;
import io.qameta.allure.ee.client.dto.Page;
import io.qameta.allure.ee.client.dto.SharedStep;
import io.qameta.allure.ee.client.dto.TestCase;
import okhttp3.Dispatcher;
import picocli.CommandLine;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

    @CommandLine.Option(
            names = {"--thread.count"},
            description = "Thread count",
            defaultValue = "${env:THREAD_COUNT}"
    )
    protected Integer threadCount;

    public abstract void runUnsafe(final ServiceBuilder builder) throws Exception;

    @Override
    public void run() {
        try {
            final int threads = Optional.ofNullable(threadCount).orElse(10);
            Dispatcher dispatcher = new Dispatcher(Executors.newFixedThreadPool(threads));
            dispatcher.setMaxRequests(threads);
            dispatcher.setMaxRequestsPerHost(threads);
            final ServiceBuilder builder = getAllureServiceBuilder()
                    .withDispatcher(dispatcher);
            runUnsafe(builder);
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    protected int getTreadCount() {
        return Optional.ofNullable(threadCount).orElse(10);
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

    protected List<Long> getSharedSteps(final SharedStepService service,
                                        final Long projectId) throws IOException {
        final List<Long> testCases = new ArrayList<>();
        Page<SharedStep> current = new Page<SharedStep>().setNumber(-1);
        do {
            current = executeRequest(
                    service.findAll(projectId, null, false, current.getNumber() + 1, 100)
            );
            for (SharedStep item : current.getContent()) {
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


    protected boolean invokeParallel(final String description,
                                     final Collection<Long> ids,
                                     final Consumer<Long> task) throws Exception {
        System.out.printf("Starting task '%s'\n", description);

        final Instant startTime = Instant.now();
        final ExecutorService executor = Executors.newFixedThreadPool(getTreadCount());
        final List<Callable<Boolean>> tasks = new ArrayList<>();
        for (Long id : ids) {
            tasks.add(() -> {
                try {
                    task.accept(id);
                    return true;
                } catch (Throwable e) {
                    return false;
                }
            });
        }
        try {
            final List<Future<Boolean>> results = executor.invokeAll(tasks);
            int errorsCount = 0;
            for (Future<Boolean> fr : results) {
                boolean success = fr.get();
                if (!success) {
                    errorsCount++;
                }
            }
            final Instant endTime = Instant.now();
            System.out.printf(
                    "Finishing task '%s' (%s) with %s errors\n",
                    description,
                    Duration.between(startTime, endTime),
                    errorsCount
            );
        } finally {
            executor.shutdown();
        }
        return true;
    }

    @FunctionalInterface
    public interface Consumer<T> {

        void accept(T t) throws Exception;

    }
}
