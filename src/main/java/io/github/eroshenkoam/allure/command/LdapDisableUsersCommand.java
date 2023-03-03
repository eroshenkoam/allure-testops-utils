package io.github.eroshenkoam.allure.command;

import io.qameta.allure.ee.client.AccountService;
import io.qameta.allure.ee.client.GroupService;
import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.dto.Account;
import io.qameta.allure.ee.client.dto.Authority;
import io.qameta.allure.ee.client.dto.Page;
import okhttp3.ResponseBody;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.query.LdapQueryBuilder;
import picocli.CommandLine;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@CommandLine.Command(
        name = "disable-ldap-users", mixinStandardHelpOptions = true,
        description = "Disable ldap users in Allure TestOps"
)
public class LdapDisableUsersCommand extends AbstractTestOpsCommand {

    @CommandLine.Option(
            names = {"--ldap.url"},
            description = "Ldap URL",
            defaultValue = "${env:LDAP_URL}"
    )
    protected String ldapUrl;

    @CommandLine.Option(
            names = {"--ldap.userDN"},
            description = "Ldap User DN",
            defaultValue = "${env:LDAP_USERDN}"
    )
    protected String ldapUserDN;

    @CommandLine.Option(
            names = {"--ldap.password"},
            description = "Ldap URL",
            defaultValue = "${env:LDAP_PASSWORD}"
    )
    protected String ldapPassword;

    @CommandLine.Option(
            names = {"--ldap.referral"},
            description = "Ldap referral",
            defaultValue = "${env:LDAP_REFERRAL}"
    )
    protected String ldapReferral;

    @CommandLine.Option(
            names = {"--ldap.uidAttribute"},
            description = "Ldap UID attribute",
            defaultValue = "${env:LDAP_UIDATTRIBUTE}"
    )
    protected String uidAttribute;

    @CommandLine.Option(
            names = {"--ldap.disabledAttribute"},
            description = "Ldap disabled attribute",
            defaultValue = "${env:LDAP_DISABLEDATTRIBUTE}"
    )
    protected String disableAttribute;

    @CommandLine.Option(
            names = {"--ldap.userSearchBase"},
            description = "Ldap user search base",
            defaultValue = "${env:LDAP_USERSEARCHBASE}"
    )
    protected String userSearchBase;

    @CommandLine.Option(
            names = {"--ldap.userSearchFilter"},
            description = "Ldap user search filter",
            defaultValue = "${env:LDAP_USERSEARCHFILTER}"
    )
    protected String userSearchFilter;

    @Override
    public void runUnsafe(final ServiceBuilder builder) throws Exception {
        final AccountService accountService = builder.create(AccountService.class);
        final List<String> usernames = getAllureUsernames(accountService);
        System.out.printf("Prepare to sync %s users from Allure\n", usernames);

        final List<String> usersForDisable = getUsersForDisable(usernames);
        System.out.printf("Found information about %s disabled users in Ldap\n", usersForDisable);

        disableUsers(usersForDisable, accountService);
        System.out.print("All users disabled successfully\n");
    }

    public List<String> getUsersForDisable(final List<String> usernames) {
        final LdapTemplate ldapTemplate = createTemplate();
        return usernames.stream()
                .map(username -> {
                    try {
                        final DirContextOperations context = ldapTemplate.searchForContext(
                                LdapQueryBuilder.query().base(userSearchBase).filter(userSearchFilter, username)
                        );
                        return Optional.of(context);
                    } catch (IncorrectResultSizeDataAccessException | NameNotFoundException e) {
                        return Optional.empty();
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(DirContextOperations.class::cast)
                .filter(d -> d.attributeExists(disableAttribute))
                .map(d -> d.getStringAttribute(uidAttribute))
                .collect(Collectors.toList());
    }

    private void disableUsers(final List<String> users, final AccountService service) throws IOException {
        for (String user : users) {
            final Response<Account> userResponse = service.findByUsername(user).execute();
            if (!userResponse.isSuccessful()) {
                throw new RuntimeException(userResponse.message());
            }
            final Long id = userResponse.body().getId();
            final Response<ResponseBody> authorityResponse = service
                    .setAuthority(id, List.of(Authority.ROLE_AUDITOR)).execute();
            if (!authorityResponse.isSuccessful()) {
                throw new RuntimeException(authorityResponse.message());
            }
        }
    }

    private LdapTemplate createTemplate() {
        final LdapContextSource source = new LdapContextSource();
        source.setUrl(ldapUrl);
        source.setUserDn(ldapUserDN);
        source.setPassword(ldapPassword);
        Optional.ofNullable(ldapReferral).ifPresent(source::setReferral);
        source.afterPropertiesSet();
        return new LdapTemplate(source);
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
