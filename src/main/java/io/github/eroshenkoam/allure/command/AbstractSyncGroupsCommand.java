package io.github.eroshenkoam.allure.command;

import io.qameta.allure.ee.client.AccountService;
import io.qameta.allure.ee.client.GroupService;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.dto.Account;
import io.qameta.allure.ee.client.dto.Group;
import io.qameta.allure.ee.client.dto.GroupUser;
import io.qameta.allure.ee.client.dto.Page;
import picocli.CommandLine;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractSyncGroupsCommand implements Runnable {

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

    public abstract Map<String, List<String>> getGroups(final List<String> usernames) throws IOException;

    @Override
    public void run() {
        try {
            runUnsafe();
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    private ServiceBuilder getAllureServiceBuilder() {
        return new ServiceBuilder(allureEndpoint)
                .authBasic(allureUsername, allurePassword);
    }

    private void runUnsafe() throws Exception {
        final ServiceBuilder builder = getAllureServiceBuilder();
        final AccountService accountService = builder.create(AccountService.class);
        final List<String> usernames = getAllureUsernames(accountService);
        System.out.printf("Prepare to sync %s users from Allure\n", usernames);

        final Map<String, List<String>> groupUsers = getGroups(usernames);
        System.out.printf("Found information about %s in Crowd\n", groupUsers.keySet());

        final GroupService groupService = builder.create(GroupService.class);
        syncAllureGroups(groupUsers, groupService);
        System.out.print("All groups synced successfully\n");
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

    private Set<GroupUser> toGroupUsers(final Long groupId, final List<String> users) {
        return users.stream()
                .map(user -> new GroupUser().setGroupId(groupId).setUsername(user))
                .collect(Collectors.toSet());
    }

    private List<String> getAllureUsernames(final AccountService accountService) throws IOException {

        final Response<Page<Account>> accountsResponse = accountService.getAccounts().execute();
        if (!accountsResponse.isSuccessful()) {
            throw new RuntimeException(accountsResponse.message());
        }
        final List<Account> accounts = accountsResponse.body().getContent();
        return accounts.stream()
                .map(Account::getUsername)
                .collect(Collectors.toList());
    }

}
