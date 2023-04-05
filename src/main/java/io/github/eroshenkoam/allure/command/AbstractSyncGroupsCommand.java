package io.github.eroshenkoam.allure.command;

import io.qameta.allure.ee.client.AccountService;
import io.qameta.allure.ee.client.GroupService;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.dto.*;
import picocli.CommandLine;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractSyncGroupsCommand extends AbstractTestOpsCommand {

    public abstract Map<String, List<String>> getGroups(final List<String> usernames) throws IOException;

    @Override
    public void runUnsafe(final ServiceBuilder builder) throws Exception {
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
        final Response<Page<Group>> groupsResponse = groupService.find("", 0, 1000).execute();
        if (!groupsResponse.isSuccessful()) {
            throw new RuntimeException(groupsResponse.message());
        }
        final Page<Group> groups = groupsResponse.body();

        for (Map.Entry<String, List<String>> entry : groupUsers.entrySet()) {
            final String name = entry.getKey();
            final List<String> usernames = entry.getValue();

            final Optional<Group> existing = groups.getContent().stream()
                    .filter(group -> group.getName().equals(name))
                    .findAny();

            if (existing.isPresent()) {
                final Group group = existing.get();
                final Response<Page<GroupUser>> groupUsersResponse = groupService.getUsers(group.getId()).execute();
                if (!groupUsersResponse.isSuccessful()) {
                    throw new RuntimeException(groupUsersResponse.message());
                }
                for (GroupUser user : groupUsersResponse.body().getContent()) {
                    final Response<Void> removeResponse = groupService
                            .removeUser(group.getId(), user.getUsername()).execute();
                    if (!removeResponse.isSuccessful()) {
                        throw new RuntimeException(removeResponse.message());
                    }
                }
                final GroupUserAdd users = new GroupUserAdd().setUsernames(usernames);
                final Response<Void> addResponse = groupService.addUsers(group.getId(), users).execute();
                if (!addResponse.isSuccessful()) {
                    throw new RuntimeException(addResponse.message());
                }
            } else {
                final Group group = new Group()
                        .setName(name);
                final Response<Group> createdResponse = groupService.create(group).execute();
                if (!createdResponse.isSuccessful()) {
                    throw new RuntimeException(createdResponse.message());
                }
                final Group created = createdResponse.body();
                final GroupUserAdd users = new GroupUserAdd().setUsernames(usernames);
                final Response<Void> addResponse = groupService.addUsers(created.getId(), users).execute();
                if (!addResponse.isSuccessful()) {
                    throw new RuntimeException(addResponse.message());
                }
            }

        }

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
