package io.github.eroshenkoam.allure.command;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.TestResultService;
import io.qameta.allure.ee.client.dto.Page;
import io.qameta.allure.ee.client.dto.TestResultScenario;
import io.qameta.allure.ee.client.dto.TestResultStep;
import io.qameta.allure.model.StepResult;
import io.qameta.allure.model.TestResult;
import picocli.CommandLine;
import retrofit2.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@CommandLine.Command(
        name = "export-testresults", mixinStandardHelpOptions = true,
        description = "Export test results to allure results"
)
public class ExportTestResultsCommand extends AbstractTestOpsCommand {

    @CommandLine.Option(
            names = {"--allure.project.id"},
            description = "Allure TestOps project id",
            defaultValue = "${env:ALLURE_PROJECT_ID}",
            required = true
    )
    protected Long allureProjectId;

    @CommandLine.Option(
            names = {"--allure.testresult.filter"},
            description = "Test Result filter",
            defaultValue = "${env:ALLURE_TESTRESULT_FILTER}",
            required = true
    )
    protected String allureTestResultFilter;

    @CommandLine.Option(
            names = {"--output-dir"},
            description = "Export output directory",
            defaultValue = "./output"
    )
    protected Path outputPath;

    @Override
    public void runUnsafe(ServiceBuilder builder) throws Exception {
        final TestResultService service = builder.create(TestResultService.class);
        System.out.printf("Export results from project [%s] filter [%s]\n", allureProjectId, allureTestResultFilter);

        final List<TestResult> testResults = getTestResults(service);
        System.out.printf("Found [%s] results\n", testResults.size());

        Files.createDirectories(outputPath);

        final ObjectMapper mapper = new ObjectMapper()
                .enable(JsonGenerator.Feature.IGNORE_UNKNOWN);

        for (final TestResult result : testResults) {
            final Path testResultPath = outputPath
                    .resolve(String.format("%s-result.json", result.getUuid()));
            mapper.writeValue(testResultPath.toFile(), result);
        }
    }

    private List<TestResult> getTestResults(final TestResultService service) throws IOException {
        final List<TestResult> testCases = new ArrayList<>();
        Page<io.qameta.allure.ee.client.dto.TestResult> current =
                new Page<io.qameta.allure.ee.client.dto.TestResult>().setNumber(-1);
        do {
            current = executeRequest(
                    service.findByRql(allureProjectId, allureTestResultFilter, current.getNumber() + 1, 10)
            );
            for (io.qameta.allure.ee.client.dto.TestResult info : current.getContent()) {
                final io.qameta.allure.ee.client.dto.TestResult origin = executeRequest(service.findById(info.getId()));
                final TestResult testResult = convertTestResult(origin);

                final TestResultScenario scenario = executeRequest(service.getScenario(info.getId()));
                if (Objects.nonNull(scenario)) {
                    testResult.setSteps(convertScenario(scenario.getSteps()));
                }
                testCases.add(testResult);
            }
        } while (current.getNumber() < current.getTotalPages());
        return testCases;
    }

    private TestResult convertTestResult(final io.qameta.allure.ee.client.dto.TestResult origin) {
        return new TestResult()
                .setName(origin.getName())
                .setUuid(UUID.randomUUID().toString())
                .setDescription(origin.getDescription())
                .setFullName(origin.getFullName())
                .setStart(origin.getStart())
                .setStop(origin.getStop());
    }

    private List<StepResult> convertScenario(final List<TestResultStep> steps) {
        final List<StepResult> result = new ArrayList<>();
        if (Objects.nonNull(steps)) {
            steps.forEach(step -> {
                final StepResult item = new StepResult()
                        .setName(step.getName())
                        .setStart(step.getStart())
                        .setStop(step.getStop());
                item.setSteps(convertScenario(step.getSteps()));
                result.add(item);
            });
        }
        return result;
    }
}
