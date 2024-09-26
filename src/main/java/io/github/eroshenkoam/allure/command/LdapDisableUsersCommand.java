package io.github.eroshenkoam.allure.command;

import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.ldap.NameNotFoundException;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;
import org.springframework.ldap.query.LdapQueryBuilder;
import picocli.CommandLine;

import java.util.*;

@CommandLine.Command(
        name = "disable-ldap-users", mixinStandardHelpOptions = true,
        description = "Disable ldap users in Allure TestOps"
)
public class LdapDisableUsersCommand extends AbstractDisableUsersCommand {

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
            names = {"--ldap.disabledAttributeName"},
            description = "Ldap disabled attribute name",
            defaultValue = "${env:LDAP_DISABLEDATTRIBUTENAME}"
    )
    protected String disableAttributeName;

    @CommandLine.Option(
            names = {"--ldap.disabledAttributeValue"},
            description = "Ldap disabled attribute value",
            defaultValue = "${env:LDAP_DISABLEDATTRIBUTEVALUE}"
    )
    protected String disableAttributeValue;

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
    public List<String> getUsersForDisable(final List<String> usernames) {
        final List<String> usersForDisable = new ArrayList<>();
        final LdapTemplate ldapTemplate = createTemplate();
        for (String username : usernames) {
            System.out.printf("Looking for [%s] in ldap\n", username);
            try {
                final DirContextOperations context = ldapTemplate.searchForContext(
                        LdapQueryBuilder.query().base(userSearchBase).filter(userSearchFilter, username)
                );
                if (Objects.nonNull(context)) {
                    System.out.printf("Found information for [%s] in ldap\n", username);
                    final boolean attributeExists = context.attributeExists(disableAttributeName);
                    if (attributeExists) {
                        if (Objects.isNull(disableAttributeValue)) {
                            System.out.printf("User [%s] should be disabled: attribute exists\n", username);
                            usersForDisable.add(username);
                        } else {
                            final String attributeValue = context.getStringAttribute(disableAttributeName);
                            if (disableAttributeValue.equals(attributeValue)) {
                                System.out.printf(
                                        "User [%s] should be disabled: attribute value match [%s]\n",
                                        username, disableAttributeValue
                                );
                                usersForDisable.add(username);
                            } else {
                                System.out.printf(
                                        "User [%s] attribute [%s] value [%s] not match [%s]\n",
                                        username, disableAttributeName, attributeValue, disableAttributeValue
                                );
                            }
                        }
                    } else {
                        System.out.printf("User [%s] have no attribute [%s]\n", username, disableAttributeName);
                    }
                }
            } catch (IncorrectResultSizeDataAccessException | NameNotFoundException e) {
                System.out.printf("User [%s] error: %s\n", username, e.getMessage());
            }
        }
        return usersForDisable;
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
}
