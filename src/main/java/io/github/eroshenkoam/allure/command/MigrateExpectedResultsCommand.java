package io.github.eroshenkoam.allure.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.TestCaseService;
import io.qameta.allure.ee.client.dto.Scenario;
import io.qameta.allure.ee.client.dto.Step;
import picocli.CommandLine;
import retrofit2.Response;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

@CommandLine.Command(
        name = "migrate-expected-results", mixinStandardHelpOptions = true,
        description = "Migrate scenario to expected results"
)
public class MigrateExpectedResultsCommand extends AbstractTestOpsCommand {

    @CommandLine.Option(
            names = {"--allure.project.id"},
            description = "Allure TestOps project id",
            defaultValue = "${env:ALLURE_PROJECT_ID}",
            required = true
    )
    protected Long allureProjectId;

    @CommandLine.Option(
            names = {"--allure.testcase.filter"},
            description = "Allure TestOps test case filter",
            defaultValue = "${env:ALLURE_TESTCASE_FILTER}",
            required = true
    )
    protected String allureTestCaseFilter;

    @CommandLine.Option(
            names = {"--backup-dir"},
            description = "Backup output directory",
            defaultValue = "./backup"
    )
    protected Path backupPath;

    private final ObjectMapper mapper = new ObjectMapper()
            .disable(FAIL_ON_UNKNOWN_PROPERTIES);

    @Override
    public void runUnsafe(ServiceBuilder builder) throws Exception {
        final TestCaseService service = builder.create(TestCaseService.class);
        final List<Long> testCaseIds = getTestCases(service, allureProjectId, allureTestCaseFilter);
        Files.createDirectories(backupPath);
        for (Long testCaseId : testCaseIds) {
            migrateTestCaseScenario(service, testCaseId);
        }
    }

    public void migrateTestCaseScenario(final TestCaseService service,
                                        final Long testCaseId) throws IOException {
        System.out.printf("Migrating scenario for test case: %s\n", testCaseId);
        final Response<Scenario> scenarioResponse = service.getScenario(testCaseId).execute();
        if (!scenarioResponse.isSuccessful()) {
            throw new RuntimeException(scenarioResponse.message());
        }
        final Scenario oldScenario = scenarioResponse.body();
        if (Objects.isNull(oldScenario) || Objects.isNull(oldScenario.getSteps())) {
            System.out.printf("Skipping migration for test case with empty scenario: %s\n", testCaseId);
            return;
        }
        if (!isScenarioContainsOnlyExpectedResults(oldScenario)) {
            System.out.printf("Skipping migration for test case without steps with expected results: %s\n", testCaseId);
            return;
        }
        final Path scenarioPath = backupPath.resolve(String.format("%s.json", testCaseId));
        final String scenarioContent = mapper.writeValueAsString(oldScenario);
        Files.write(scenarioPath, scenarioContent.getBytes(StandardCharsets.UTF_8));
        System.out.printf("Scenario saved for test case: %s\n", testCaseId);

        final Scenario newScenario = migrateScenario(oldScenario);
        System.out.printf("Scenario converted for test case: %s\n", testCaseId);

        final Response<Void> updateResponse = service.setScenario(testCaseId, newScenario).execute();
        if (!updateResponse.isSuccessful()) {
            throw new RuntimeException(updateResponse.message());
        }
        System.out.printf("Scenario migrated successfully for test case: %s\n", testCaseId);
    }

    private boolean isScenarioContainsOnlyExpectedResults(final Scenario scenario) {
        if (Objects.nonNull(scenario) && Objects.nonNull(scenario.getSteps())) {
            return scenario.getSteps().stream()
                    .allMatch(s -> {
                        if (Objects.nonNull(s.getExpectedResult())) {
                            return false;
                        }
                        final List<Step> subSteps = s.getSteps();
                        if(Objects.isNull(subSteps) || s.getSteps().size() == 0) {
                            return true;
                        }
                        if (subSteps.size() == 1) {
                            final Step expectedResult = subSteps.get(0);
                            final List<Step> expectedResultSubSteps = expectedResult.getSteps();
                            return Objects.isNull(expectedResultSubSteps) || expectedResultSubSteps.size() == 0;
                        }
                        return false;
                    });
        }
        return false;
    }

    private Scenario migrateScenario(final Scenario oldScenario) {
        final Scenario newScenario = new Scenario();
        newScenario.setSteps(new ArrayList<>());
        oldScenario.getSteps().stream()
                .map(this::migrateStep)
                .forEach(newScenario.getSteps()::add);
        return newScenario;
    }

    private Step migrateStep(final Step oldStep) {
        final Step stepWithExpectedResult = new Step()
                .setSteps(new ArrayList<>())
                .setAttachments(new ArrayList<>());
        stepWithExpectedResult.setName(oldStep.getName());
        Optional.ofNullable(oldStep.getAttachments())
                .ifPresent(stepWithExpectedResult.getAttachments()::addAll);
        if (Objects.nonNull(oldStep.getSteps()) && oldStep.getSteps().size() == 1) {
            final Step subStep = oldStep.getSteps().get(0);
            stepWithExpectedResult.setExpectedResult(subStep.getName());
            Optional.ofNullable(subStep.getAttachments())
                    .ifPresent(stepWithExpectedResult.getAttachments()::addAll);
        }
        return stepWithExpectedResult;
    }

}
