package io.github.eroshenkoam.allure.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eroshenkoam.allure.gitlab.GitlabGroup;
import io.github.eroshenkoam.allure.gitlab.GitlabMember;
import io.github.eroshenkoam.allure.gitlab.GitlabMembership;
import io.github.eroshenkoam.allure.gitlab.GitlabService;
import io.github.eroshenkoam.allure.retrofit.GitlabInterceptor;
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

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

@CommandLine.Command(
        name = "sync-gitlab-groups", mixinStandardHelpOptions = true,
        description = "Sync Gitlab groups with Allure TestOps"
)
public class GitlabSyncGroupsCommand extends AbstractSyncGroupsCommand {

    @CommandLine.Option(
            names = {"--gitlab.endpoint"},
            description = "Gitlab endpoint",
            defaultValue = "${env:GITLAB_ENDPOINT}"
    )
    protected String gitlabEndpoint;

    @CommandLine.Option(
            names = {"--gitlab.token"},
            description = "Gitlab token",
            defaultValue = "${env:GITLAB_TOKEN}"
    )
    protected String gitlabToken;

    @CommandLine.Option(
            names = {"--gitlab.create.subgroups"},
            description = "Create gitlab subgroups",
            defaultValue = "${env:GITLAB_CREATE_SUBGROUPS}"
    )
    protected boolean createSubgroups;

    @Override
    public Map<String, List<String>> getGroups(final List<String> usernames) throws IOException {
        final OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new GitlabInterceptor(gitlabToken))
                .build();

        final ObjectMapper mapper = new ObjectMapper()
                .disable(FAIL_ON_UNKNOWN_PROPERTIES);

        final String fixedEndpoint = gitlabEndpoint.endsWith("/") ? gitlabEndpoint : gitlabEndpoint + "/";
        final Retrofit retrofit = new Retrofit.Builder()
                .addConverterFactory(JacksonConverterFactory.create(mapper))
                .baseUrl(fixedEndpoint)
                .client(client)
                .build();

        final GitlabService gitlabService = retrofit.create(GitlabService.class);
        final Map<String, List<String>> result = new HashMap<>();
        for (String username : usernames) {
            final Response<List<GitlabMember>> userResponse = gitlabService.getUser(username).execute();
            if (userResponse.isSuccessful() && userResponse.body().size() == 1) {
                System.out.printf("Sync groups for [%s]\n", username);
                final List<GitlabMembership> memberships = executeRequest(
                        gitlabService.getUserMembers(userResponse.body().get(0).getId())
                );
                final List<Long> groupIds = memberships.stream()
                        .filter(membership -> membership.getSourceType().equals("Namespace"))
                        .map(GitlabMembership::getSourceId)
                        .toList();
                for (Long groupId : groupIds) {
                    final List<GitlabGroup> groups = getAllGroups(gitlabService, groupId, createSubgroups);
                    for (GitlabGroup group : groups) {
                        final String groupPath = group.getFullPath();
                        final List<String> users = result.getOrDefault(groupPath, new ArrayList<>());
                        users.add(username);
                        result.put(groupPath, users);
                    }
                }
            }
        }
        return result;
    }

    private List<GitlabGroup> getAllGroups(final GitlabService gitlabService,
                                           final Long groupId,
                                           final boolean includeSubgroups) throws IOException {
        final GitlabGroup group = executeRequest(gitlabService.getGroup(groupId));

        final List<GitlabGroup> result = new ArrayList<>();
        result.add(group);
        if (includeSubgroups) {
            result.addAll(getSubgroups(gitlabService, group));
        }
        return result;
    }

    private List<GitlabGroup> getSubgroups(final GitlabService gitlabService,
                                           final GitlabGroup group) throws IOException {
        final List<GitlabGroup> result = new ArrayList<>();
        final List<GitlabGroup> subgroups = executeRequest(gitlabService.getSubgroups(group.getId()));
        for (GitlabGroup subgroup : subgroups) {
            result.add(subgroup);
            result.addAll(getSubgroups(gitlabService, subgroup));
        }
        return result;
    }

}
