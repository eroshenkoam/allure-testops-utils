package io.github.eroshenkoam.allure.command;

import io.qameta.allure.ee.client.LaunchService;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.dto.Launch;
import io.qameta.allure.ee.client.dto.Page;
import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import picocli.CommandLine;
import retrofit2.Response;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@CommandLine.Command(
        name = "clean-launches", mixinStandardHelpOptions = true,
        description = "Delete launches by filter and date"
)
public class LaunchCleanCommand extends AbstractTestOpsCommand {

    @CommandLine.Option(
            names = {"--project.id"},
            description = "Delete launch in project",
            defaultValue = "${env:PROJECT_ID}",
            required = true
    )
    protected Long projectId;

    @CommandLine.Option(
            names = {"--launch.filter"},
            description = "Delete launch by filter",
            defaultValue = "${env:LAUNCH_FILTER}",
            required = true
    )
    protected String launchFilter;

    @CommandLine.Option(
            names = {"--launch.createdBefore"},
            description = "Delete launch before this date",
            defaultValue = "${env:LAUNCH_CREATEDBEFORE}"
    )
    protected String launchCreatedBefore;

    @Override
    public void runUnsafe(final ServiceBuilder builder) throws Exception {
        final StringBuilder launchQuery = new StringBuilder();

        Optional.ofNullable(launchFilter)
                .ifPresent(launchQuery::append);
        Optional.ofNullable(launchCreatedBefore)
                .map(this::parseCreatedBefore)
                .ifPresent(createdBefore -> launchQuery.append(" and ").append("createdDate < ").append(createdBefore));
        System.out.printf("Prepare to delete launches by query [%s]\n", launchQuery);

        final LaunchService service = builder.create(LaunchService.class);

        final List<Launch> launchesToDelete = getLaunches(service, launchQuery.toString());
        System.out.printf("Found [%s] launches by query [%s]\n", launchesToDelete.size(), launchQuery);

        for (Launch launch : launchesToDelete) {
            final Response<Launch> response = service.delete(launch.getId()).execute();
            if (!response.isSuccessful()) {
                throw new RuntimeException("Can not delete launch: " + response.message());
            }
        }
    }

    private List<Launch> getLaunches(final LaunchService service, final String launchQuery) throws IOException {
        final List<Launch> launches = new ArrayList<>();
        Page<Launch> current = new Page<Launch>().setNumber(-1);
        do {
            final Response<Page<Launch>> response = service
                    .findAll(projectId, launchQuery, current.getNumber() + 1, 10)
                    .execute();
            if (!response.isSuccessful()) {
                throw new RuntimeException("Can not find launches: " + response.message());
            }
            current = response.body();
            launches.addAll(current.getContent());
        } while (current.getNumber() < current.getTotalPages());
        return launches;
    }

    private Long parseCreatedBefore(String time) {
        final PeriodFormatter formatter = new PeriodFormatterBuilder()
                .appendDays().appendSuffix("d").appendSeparatorIfFieldsAfter(" ")
                .appendHours().appendSuffix("h").appendSeparatorIfFieldsAfter(" ")
                .appendMinutes().appendSuffix("m").appendSeparatorIfFieldsAfter(" ")
                .toFormatter();
        final Period period = formatter.parsePeriod(time);
        return System.currentTimeMillis() - period.toStandardDuration().getMillis();
    }

}
