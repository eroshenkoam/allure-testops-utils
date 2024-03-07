package io.github.eroshenkoam.allure.command;

import io.qameta.allure.ee.client.AccountService;
import io.qameta.allure.ee.client.GroupService;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.dto.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        final Page<Group> groups = executeRequest(groupService.find("", 0, 1000));

        for (Map.Entry<String, List<String>> entry : groupUsers.entrySet()) {
            final String name = entry.getKey();
            final List<String> usernames = entry.getValue();

            final Optional<Group> existing = groups.getContent().stream()
                    .filter(group -> group.getName().equals(name))
                    .findAny();

            if (existing.isPresent()) {
                final Group group = existing.get();
                final Page<GroupUser> groupUsersList = executeRequest(groupService.getUsers(group.getId()));
                for (GroupUser user : groupUsersList.getContent()) {
                    executeRequest(groupService.removeUser(group.getId(), user.getUsername()));
                }
                final GroupUserAdd users = new GroupUserAdd().setUsernames(usernames);
                executeRequest(groupService.addUsers(group.getId(), users));
            } else {
                final Group group = new Group()
                        .setName(name);
                final Group created = executeRequest(groupService.create(group));
                final GroupUserAdd users = new GroupUserAdd().setUsernames(usernames);
                executeRequest(groupService.addUsers(created.getId(), users));
            }

        }

    }

    private List<String> getAllureUsernames(final AccountService accountService) throws IOException {

        final List<Account> accounts = executeRequest(accountService.getAccounts()).getContent();
        return accounts.stream()
                .map(Account::getUsername)
                .collect(Collectors.toList());
    }

}
