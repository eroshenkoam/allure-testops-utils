package io.github.eroshenkoam.allure.command;

import io.qameta.allure.ee.client.IntegrationService;
import io.qameta.allure.ee.client.JobService;
import io.qameta.allure.ee.client.ProjectService;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.dto.Job;
import io.qameta.allure.ee.client.dto.Page;
import io.qameta.allure.ee.client.dto.Project;
import io.qameta.allure.ee.client.dto.ProjectIntegration;
import okhttp3.ResponseBody;
import picocli.CommandLine;
import retrofit2.Response;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@CommandLine.Command(
        name = "clean-integrations", mixinStandardHelpOptions = true,
        description = "Clean unused integrations"
)
public class CleanIntegrationsCommand extends AbstractTestOpsCommand {

    @CommandLine.Option(
            names = {"--allure.project.id"},
            description = "Allure TestOps project id",
            defaultValue = "${env:ALLURE_PROJECT_ID}"
    )
    protected Long allureProjectId;

    @CommandLine.Option(
            names = {"--allure.integration.type"},
            description = "Allure TestOps integration type",
            defaultValue = "${env:ALLURE_INTEGRATION_TYPE}",
            required = true
    )
    protected Set<String> allureIntegrationTypes;

    @Override
    public void runUnsafe(final ServiceBuilder builder) throws Exception {
        final ProjectService service = builder.create(ProjectService.class);
        if (Objects.isNull(allureProjectId)) {
            final Response<Page<Project>> projectsResponse = service.getProjects("", 0, 1000).execute();
            if (!projectsResponse.isSuccessful()) {
                throw new RuntimeException(projectsResponse.message());
            }
            for (Project project : projectsResponse.body().getContent()) {
                cleanProjectIntegrations(builder, project);
            }
        } else {
            final Response<Project> projectResponse = service.getProject(allureProjectId).execute();
            if (!projectResponse.isSuccessful()) {
                throw new RuntimeException(projectResponse.message());
            }
            cleanProjectIntegrations(builder, projectResponse.body());
        }
    }

    public void cleanProjectIntegrations(final ServiceBuilder builder, final Project project) throws Exception {
        System.out.printf("Clean integrations for project [%d]%n", project.getId());
        final JobService jobService = builder.create(JobService.class);
        final Response<Page<Job>> jobsResponse = jobService.getProjectJobs(project.getId(), 0, 1000).execute();
        if (!jobsResponse.isSuccessful()) {
            throw new RuntimeException(jobsResponse.message());
        }
        final Set<Long> jobIntegrationsIds = jobsResponse.body().getContent().stream()
                .map(Job::getIntegrationId)
                .collect(Collectors.toSet());
        System.out.printf("Found active integrations [%s]%n", jobIntegrationsIds);

        final IntegrationService integrationService = builder.create(IntegrationService.class);
        final Response<Page<ProjectIntegration>> integrationsResponse = integrationService
                .getProjectIntegrations(project.getId(), 0, 1000).execute();
        if (!integrationsResponse.isSuccessful()) {
            throw new RuntimeException(integrationsResponse.message());
        }
        final Set<Long> projectIntegrationIds = integrationsResponse.body().getContent().stream()
                .filter(i -> allureIntegrationTypes.contains(i.getInfo().getType()))
                .map(ProjectIntegration::getId)
                .collect(Collectors.toSet());
        System.out.printf("Found project integrations [%s]%n", projectIntegrationIds);

        final Set<Long> toDelete = new HashSet<>(projectIntegrationIds);
        toDelete.removeAll(jobIntegrationsIds);
        System.out.printf("Prepare integrations [%s] to delete%n", toDelete);

        for (Long integrationToDelete : toDelete) {
            final Response<ResponseBody> deleteIntegrationResponse = integrationService
                    .deleteProjectIntegration(integrationToDelete, project.getId()).execute();
            if (!deleteIntegrationResponse.isSuccessful()) {
                throw new RuntimeException(deleteIntegrationResponse.message());
            }
        }
    }

}
