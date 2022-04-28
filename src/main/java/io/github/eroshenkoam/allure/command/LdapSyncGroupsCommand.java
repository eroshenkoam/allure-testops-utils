package io.github.eroshenkoam.allure.command;

import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.query.LdapQueryBuilder;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@CommandLine.Command(
        name = "sync-ldap-groups", mixinStandardHelpOptions = true,
        description = "Sync Atlassian Crowd groups with Allure TestOps"
)
public class LdapSyncGroupsCommand extends AbstractSyncGroupsCommand {

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
            defaultValue = "${env:CROWD_UIDATTRIBUTE}"
    )
    protected String uidAttribute;

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

    @CommandLine.Option(
            names = {"--ldap.groupSearchBase"},
            description = "Ldap user search base",
            defaultValue = "${env:LDAP_GROUPSEARCHBASE}"
    )
    protected String groupSearchBase;

    @CommandLine.Option(
            names = {"--ldap.groupSearchFilter"},
            description = "Ldap group search filter",
            defaultValue = "${env:LDAP_GROUPSEARCHFILTER}"
    )
    protected String groupSearchFilter;

    @CommandLine.Option(
            names = {"--ldap.groupRoleAttribute"},
            description = "Ldap group role attribute",
            defaultValue = "${env:LDAP_GROUPROLEATTRIBUTE}"
    )
    protected String groupRoleAttribute;

    public Map<String, List<String>> getGroups(final List<String> usernames) {
        final LdapContextSource source = new LdapContextSource();
        source.setUrl(ldapUrl);
        source.setUserDn(ldapUserDN);
        source.setPassword(ldapPassword);
        source.setReferral(ldapReferral);
        final LdapTemplate ldapTemplate = new LdapTemplate(source);

        final Map<String, String> dns = usernames.stream()
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
                .collect(Collectors.toMap(
                        d -> d.getStringAttribute(uidAttribute),
                        DirContextOperations::getNameInNamespace)
                );

        final Map<String, List<String>> groupUsers = new HashMap<>();
        dns.forEach((username, dn) -> {
            DirContextOperations dirContextOperations = ldapTemplate.searchForContext(
                    LdapQueryBuilder.query().base(groupSearchBase).filter(groupSearchFilter, dn)
            );
            List<String> groups = new ArrayList<>();
            groups.forEach(group -> {
                final List<String> users = groupUsers.getOrDefault(group, new ArrayList<>());
                users.add(username);
                groupUsers.put(group, users);
            });
        });
        return groupUsers;
    }


}
