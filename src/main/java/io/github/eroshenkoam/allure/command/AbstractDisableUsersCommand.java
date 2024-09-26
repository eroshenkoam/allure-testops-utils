package io.github.eroshenkoam.allure.command;

import io.qameta.allure.ee.client.AccountService;
import io.qameta.allure.ee.client.AdminAccountService;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.dto.Account;
import io.qameta.allure.ee.client.dto.AccountAuthority;
import io.qameta.allure.ee.client.dto.Authority;
import io.qameta.allure.ee.client.dto.Page;
import okhttp3.ResponseBody;
import retrofit2.Response;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractDisableUsersCommand extends AbstractTestOpsCommand {

    public abstract List<String> getUsersForDisable(final List<String> usernames) throws Exception;

    @Override
    public void runUnsafe(final ServiceBuilder builder) throws Exception {
        final AccountService accountService = builder.create(AccountService.class);
        final AdminAccountService adminAccountService = builder.create(AdminAccountService.class);
        final List<String> usernames = getAllureUsernames(accountService);
        System.out.printf("Prepare to sync %s users from Allure\n", usernames);

        final List<String> usersForDisable = getUsersForDisable(usernames);
        System.out.printf("Found information about %s disabled users in Ldap\n", usersForDisable);

        disableUsers(usersForDisable, accountService, adminAccountService);
        System.out.print("All users disabled successfully\n");
    }

    private void disableUsers(final List<String> users,
                              final AccountService accountService,
                              final AdminAccountService adminAccountService) throws IOException {
        for (String user : users) {
            final Response<Account> userResponse = accountService.findByUsername(user).execute();
            if (!userResponse.isSuccessful()) {
                throw new RuntimeException(userResponse.message());
            }
            final Long id = userResponse.body().getId();
            final Response<ResponseBody> authorityResponse = adminAccountService
                    .setRole(id, new AccountAuthority(Authority.ROLE_GUEST)).execute();
            if (!authorityResponse.isSuccessful()) {
                throw new RuntimeException(authorityResponse.message());
            }
        }
    }

    private List<String> getAllureUsernames(final AccountService accountService) throws IOException {

        final Response<Page<Account>> accountsResponse = accountService.getAccounts("", 0, 1000).execute();
        if (!accountsResponse.isSuccessful()) {
            throw new RuntimeException(accountsResponse.message());
        }
        final List<Account> accounts = accountsResponse.body().getContent();
        return accounts.stream()
                .map(Account::getUsername)
                .collect(Collectors.toList());
    }

}
