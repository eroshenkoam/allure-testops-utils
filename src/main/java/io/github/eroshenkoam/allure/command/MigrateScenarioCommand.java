package io.github.eroshenkoam.allure.command;

import io.qameta.allure.ee.client.ProjectService;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.TestCaseScenarioService;
import io.qameta.allure.ee.client.TestCaseService;
import io.qameta.allure.ee.client.dto.Page;
import io.qameta.allure.ee.client.dto.Project;
import picocli.CommandLine;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@CommandLine.Command(
        name = "migrate-scenario", mixinStandardHelpOptions = true,
        description = "Backup test cases scenario"
)
public class MigrateScenarioCommand extends AbstractTestOpsCommand {

    @CommandLine.Option(
            names = {"--allure.project.id"},
            description = "Allure TestOps project id",
            defaultValue = "${env:ALLURE_PROJECT_ID}",
            split = ","
    )
    protected List<Long> allureProjectIds;

    @Override
    public void runUnsafe(final ServiceBuilder builder) throws Exception {
        final TestCaseService tcService = builder.create(TestCaseService.class);
        final TestCaseScenarioService tcScenarioService = builder.create(TestCaseScenarioService.class);
        final ProjectService projectService = builder.create(ProjectService.class);

        final List<Long> projectIds = new ArrayList<>();
        if (Objects.nonNull(allureProjectIds) && !allureProjectIds.isEmpty()) {
            projectIds.addAll(allureProjectIds);
        } else {
            projectIds.addAll(getAllProjects(projectService).keySet());
        }
        for (Long projectId : projectIds) {
            migrateProject(tcService, tcScenarioService, projectId);
        }
    }

    private void migrateProject(final TestCaseService tcService,
                                final TestCaseScenarioService tcScenarioService,
                                final Long projectId) throws Exception {
        final List<Long> testCaseIds = getTestCases(tcService, projectId, "true");
        invokeParallel("migrate project shared steps", testCaseIds, (id) -> {
            System.out.printf("Migrating test case with id %s\n", id);
            executeRequest(tcScenarioService.migrateScenario(id));
        });
    }

    private Map<Long, String> getAllProjects(final ProjectService service) throws IOException {
        final Map<Long, String> result = new HashMap<>();
        Page<Project> current = new Page<Project>().setNumber(-1);
        do {
            current = executeRequest(service.getProjects("", current.getNumber() + 1, 100));
            for (Project item : current.getContent()) {
                result.put(item.getId(), item.getName());
            }
        } while (current.getNumber() < current.getTotalPages());
        return result;
    }

}
