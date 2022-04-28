# Allure TestOps Utils

## Sync auth groups with Allure TestOps

### Atlassian Crowd -> Allure TestOps

```shell
docker run -e "ALLURE_ENDPOINT=http://localhost:8080" \
           -e "ALLURE_USERNAME=admin" \
           -e "ALLURE_PASSWORD=admin" \
           -e "CROWD_ENDPOINT=http://localhost:8095/crowd" \
           -e "CROWD_USERNAME=crowd-app-name" \
           -e "CROWD_PASSWORD=crowd-app-pass" \
           -e "CROWD_GROUP_FILTER=.*" \
           ghcr.io/eroshenkoam/allure-testops-utils sync-crowd-groups
```

### Ldap -> Allure TestOps

```shell
docker run -e "ALLURE_ENDPOINT=http://localhost:8080" \
           -e "ALLURE_USERNAME=admin" \
           -e "ALLURE_PASSWORD=admin" \
           -e "LDAP_URL=ldap://localhost:389/dc=springframework,dc=org" \
           -e "LDAP_USERDN=uid=admin,ou=people,dc=springframework,dc=org" \
           -e "LDAP_PASSWORD=admin" \
           -e "LDAP_UIDATTRIBUTE=uid" \
           -e "LDAP_USERSEARCHBASE=ou=people" \
           -e "LDAP_USERSEARCHFILTER=(&(uid={0})(objectClass=person))" \
           -e "LDAP_GROUPSEARCHBASE=ou=groups" \
           -e "LDAP_GROUPSEARCHFILTER=(uniqueMember={0})" \
           -e "LDAP_GROUPROLEATTRIBUTE=cn" \
           ghcr.io/eroshenkoam/allure-testops-utils sync-ldap-groups
```
