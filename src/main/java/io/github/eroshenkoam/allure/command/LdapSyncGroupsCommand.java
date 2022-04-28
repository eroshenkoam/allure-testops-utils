package io.github.eroshenkoam.allure.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eroshenkoam.allure.crowd.CrowdGroup;
import io.github.eroshenkoam.allure.crowd.CrowdGroups;
import io.github.eroshenkoam.allure.crowd.CrowdService;
import io.github.eroshenkoam.allure.retrofit.CrowdInterceptor;
import okhttp3.OkHttpClient;
import picocli.CommandLine;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

@CommandLine.Command(
        name = "sync-crowd-groups", mixinStandardHelpOptions = true,
        description = "Sync Atlassian Crowd groups with Allure TestOps"
)
public class LdapSyncGroupsCommand extends AbstractSyncGroupsCommand {

    @CommandLine.Option(
            names = {"--crowd.endpoint"},
            description = "Atlassian Crowd endpoint",
            defaultValue = "${env:CROWD_ENDPOINT}"
    )
    protected String crowdEndpoint;

    @CommandLine.Option(
            names = {"--crowd.username"},
            description = "Atlassian Crowd username",
            defaultValue = "${env:CROWD_USERNAME}"
    )
    protected String crowdUsername;

    @CommandLine.Option(
            names = {"--crowd.password"},
            description = "Atlassian Crowd password",
            defaultValue = "${env:CROWD_PASSWORD}"
    )
    protected String crowdPassword;

    @CommandLine.Option(
            names = {"--crowd.group.filter"},
            description = "Atlassian Crowd group filter",
            defaultValue = "${env:CROWD_GROUP_FILTER}"
    )
    protected String crowdGroupFilter = ".*";

    public Map<String, List<String>> getGroups(final List<String> usernames) throws IOException {

        final OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new CrowdInterceptor(crowdUsername, crowdPassword))
                .build();

        final ObjectMapper mapper = new ObjectMapper()
                .disable(FAIL_ON_UNKNOWN_PROPERTIES);

        final String fixedEndpoint = crowdEndpoint.endsWith("/") ? crowdEndpoint : crowdEndpoint + "/";
        final Retrofit retrofit = new Retrofit.Builder()
                .addConverterFactory(JacksonConverterFactory.create(mapper))
                .baseUrl(fixedEndpoint)
                .client(client)
                .build();

        final CrowdService crowdService = retrofit.create(CrowdService.class);
        final Map<String, List<String>> result = new HashMap<>();
        for (String username : usernames) {
            final Response<CrowdGroups> groupsResponse = crowdService.getUserNestedGroups(username).execute();
            if (groupsResponse.isSuccessful()) {
                final List<CrowdGroup> groups = Optional.ofNullable(groupsResponse.body())
                        .map(CrowdGroups::getGroups)
                        .orElseGet(ArrayList::new);
                final Pattern pattern = Pattern.compile(crowdGroupFilter);
                groups.stream().map(CrowdGroup::getName)
                        .filter(name -> pattern.matcher(name).matches())
                        .forEach(name -> {
                            final List<String> users = result.getOrDefault(name, new ArrayList<>());
                            users.add(username);
                            result.put(name, users);
                        });
            }
        }
        return result;
    }


}
