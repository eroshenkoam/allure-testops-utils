package io.github.eroshenkoam.allure.sync;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eroshenkoam.allure.crowd.CrowdGroup;
import io.github.eroshenkoam.allure.crowd.CrowdGroups;
import io.github.eroshenkoam.allure.crowd.CrowdService;
import io.github.eroshenkoam.allure.retrofit.CrowdInterceptor;
import io.qameta.allure.ee.client.AccountService;
import io.qameta.allure.ee.client.GroupService;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.dto.Account;
import io.qameta.allure.ee.client.dto.Group;
import io.qameta.allure.ee.client.dto.GroupUser;
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
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

@CommandLine.Command(
        name = "sync", mixinStandardHelpOptions = true,
        description = "Sync Atlassian Crowd groups with Allure TestOps"
)
public class SyncCommand implements Runnable {

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
    protected String crowdGroupFilter;

    @Override
    public void run() {
        try {
            runUnsafe();
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    private void runUnsafe() throws Exception {
        ServiceBuilder builder = new ServiceBuilder(allureEndpoint)
                .authBasic(allureUsername, allurePassword);
        final AccountService accountService = builder.create(AccountService.class);
        final List<String> usernames = getAllureUsernames(accountService);
        System.out.printf("Prepare to sync %s users from Allure\n", usernames);

        final Map<String, List<String>> groupUsers = getCrowdGroups(usernames);
        System.out.printf("Found information about %s in Crowd\n", groupUsers.keySet());

        final GroupService groupService = builder.create(GroupService.class);
        syncAllureGroups(groupUsers, groupService);
        System.out.print("All groups synced successfully\n");
    }

    private List<String> getAllureUsernames(final AccountService accountService) throws IOException {

        final Response<List<Account>> accountsResponse = accountService.getAccounts().execute();
        if (!accountsResponse.isSuccessful()) {
            throw new RuntimeException(accountsResponse.message());
        }
        final List<Account> accounts = accountsResponse.body();
        return accounts.stream()
                .map(Account::getUsername)
                .collect(Collectors.toList());
    }

    private void syncAllureGroups(final Map<String, List<String>> groupUsers,
                                  final GroupService groupService) throws IOException {
        final Response<List<Group>> groupsResponse = groupService.findALl().execute();
        if (!groupsResponse.isSuccessful()) {
            throw new RuntimeException(groupsResponse.message());
        }
        final List<Group> groups = groupsResponse.body();

        for (Map.Entry<String, List<String>> entry : groupUsers.entrySet()) {
            final String name = entry.getKey();
            final List<String> usernames = entry.getValue();

            final Optional<Group> existing = groups.stream()
                    .filter(group -> group.getName().equals(name))
                    .findAny();

            if (existing.isPresent()) {
                final Group group = existing.get();
                for (GroupUser user : group.getUsers()) {
                    groupService.removeUser(user).execute();
                }

                for (GroupUser user : toGroupUsers(group.getId(), usernames)) {
                    groupService.addUser(user).execute();
                }
            } else {
                final Group group = new Group()
                        .setName(name);
                final Response<Group> createdResponse = groupService.create(group).execute();
                if (!createdResponse.isSuccessful()) {
                    throw new RuntimeException(createdResponse.message());
                }
                final Group created = createdResponse.body();
                for (GroupUser user : toGroupUsers(created.getId(), usernames)) {
                    groupService.addUser(user).execute();
                }
            }

        }

    }

    private Map<String, List<String>> getCrowdGroups(final List<String> usernames) throws IOException {

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

    private Set<GroupUser> toGroupUsers(final Long groupId, final List<String> users) {
        return users.stream()
                .map(user -> new GroupUser().setGroupId(groupId).setUsername(user))
                .collect(Collectors.toSet());
    }

}
