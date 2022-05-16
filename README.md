# Allure TestOps Utils

## Delete launches in Allure TestOps

```shell
docker run -e "ALLURE_ENDPOINT=http://localhost:8080" \
           -e "ALLURE_USERNAME=admin" \
           -e "ALLURE_PASSWORD=admin" \
           -e "PROJECT_ID=2" \
           -e "LAUNCH_FILTER=tag = \"delete\"" \
           -e "LAUNCH_CREATEDBEFORE=30d 0h 0m" \
           ghcr.io/eroshenkoam/allure-testops-utils clean-launches
```

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

### Gitlab -> Allure TestOps

```shell
docker run -e "ALLURE_ENDPOINT=http://localhost:8080" \
           -e "ALLURE_USERNAME=admin" \
           -e "ALLURE_PASSWORD=admin" \
           -e "GITLAB_ENDPOINT=https://github.com" \
           -e "GITLAB_TOKEN=<token>" \
           ghcr.io/eroshenkoam/allure-testops-utils sync-ldap-groups
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
