package io.github.eroshenkoam.allure.command;

import io.qameta.allure.ee.client.ServiceBuilder;
import io.qameta.allure.ee.client.TestCaseAuditService;
import io.qameta.allure.ee.client.TestCaseService;
import io.qameta.allure.ee.client.audit.TestCaseAssociationDiff;
import io.qameta.allure.ee.client.audit.TestCaseDiff;
import io.qameta.allure.ee.client.dto.CustomFieldValue;
import io.qameta.allure.ee.client.dto.TestCase;
import io.qameta.allure.ee.client.dto.TestCaseAuditEntry;
import io.qameta.allure.ee.client.dto.TestCaseAuditEntryData;
import io.qameta.allure.ee.client.dto.TestCaseAuditType;
import io.qameta.allure.ee.client.dto.TestCasePatch;
import io.qameta.allure.ee.client.dto.TestTag;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import picocli.CommandLine;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@CommandLine.Command(
        name = "rollback-testcases", mixinStandardHelpOptions = true,
        description = "Rollback test cases changes"
)
public class RollbackTestCasesCommand extends AbstractTestOpsCommand {

    @CommandLine.Option(
            names = {"--allure.project.id"},
            description = "Allure TestOps project id",
            defaultValue = "${env:ALLURE_PROJECT_ID}",
            required = true
    )
    protected Long allureProjectId;

    @CommandLine.Option(
            names = {"--allure.testcase.filter"},
            description = "Test Case filter",
            defaultValue = "${env:ALLURE_TESTCASE_FILTER}",
            required = true
    )
    protected String allureTestCaseFilter;

    @CommandLine.Option(
            names = {"--allure.audit.after"},
            description = "Rollback all audit changes after this date",
            defaultValue = "${env:ALLURE_AUDIT_AFTER}",
            required = true
    )
    protected String allureAuditAfter;

    @CommandLine.Option(
            names = {"--allure.audit.author"},
            description = "Rollback all audit changes from user author",
            defaultValue = "${env:ALLURE_AUDIT_AUTHOR}",
            required = false
    )
    protected String allureAuditAuthor;

    @Override
    public void runUnsafe(final ServiceBuilder builder) throws Exception {
        final TestCaseService service = builder.create(TestCaseService.class);
        System.out.printf("Rollback testcases from project [%s] filter [%s]\n", allureProjectId, allureTestCaseFilter);

        final List<Long> ids = getTestCases(service, allureProjectId, allureTestCaseFilter);
        for (Long id : ids) {
            rollBackTestCase(builder, id);
        }
    }

    public void rollBackTestCase(final ServiceBuilder builder, final Long testCaseId) throws Exception {
        final DateTimeFormatter dateFormat = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
        final DateTime dateTime = dateFormat.parseDateTime(allureAuditAfter);

        final TestCaseAuditService service = builder.create(TestCaseAuditService.class);

        final List<AuditAction> actions = Arrays.asList(
                new RollbackTestCaseDataAction(),
                new RollbackCustomFieldDeleteAction(),
                new RollbackCustomFieldInsertAction(),
                new RollbackTagsDeleteAction(),
                new RollbackTagsInsertAction()
        );

        final List<TestCaseAuditEntry> auditPages = service.getTestCaseAudit(testCaseId, 0, 50)
                .execute().body().getContent().stream()
                .filter(entry -> entry.getTimestamp() > dateTime.getMillis())
                .collect(Collectors.toList());
        for (TestCaseAuditEntry entry : auditPages) {
            System.out.printf("Found audit entry [%s] at [%s]%n", entry.getId(), dateFormat.print(entry.getTimestamp()));
            if (Objects.isNull(allureAuditAfter) || entry.getUsername().equals(allureAuditAuthor)) {
                for (TestCaseAuditEntryData data : entry.getData()) {
                    for (AuditAction action : actions) {
                        if (action.isApplicable(entry.getActionType(), data)) {
                            action.apply(builder, testCaseId, entry.getActionType(), data);
                        }
                    }
                }
            }
        }
    }

    public interface AuditAction {

        boolean isApplicable(final TestCaseAuditType type, final TestCaseAuditEntryData data);

        void apply(final ServiceBuilder builder,
                   final Long testCasId,
                   final TestCaseAuditType type,
                   final TestCaseAuditEntryData data) throws Exception;

    }

    public static class RollbackTestCaseDataAction implements AuditAction {

        @Override
        public boolean isApplicable(final TestCaseAuditType type, final TestCaseAuditEntryData data) {
            return TestCaseAuditType.UPDATE.equals(type) && "test_case".equals(data.getType());
        }

        @Override
        public void apply(final ServiceBuilder builder,
                          final Long testCaseId,
                          final TestCaseAuditType type,
                          final TestCaseAuditEntryData data) throws Exception {
            final TestCaseService service = builder.create(TestCaseService.class);
            final TestCaseDiff diff = (TestCaseDiff) data.getDiff();
            final TestCasePatch testCase = new TestCasePatch();
            Optional.ofNullable(diff.getName()).ifPresent(value -> {
                final String oldValue = value.getOldValue();
                System.out.printf("For test case [%s] set name [%s]%n", testCaseId, oldValue);
                testCase.setName(Optional.ofNullable(oldValue).orElse(""));
            });
            Optional.ofNullable(diff.getDescription()).ifPresent(value -> {
                final String oldValue = value.getOldValue();
                System.out.printf("For test case [%s] set description [%s]%n", testCaseId, oldValue);
                testCase.setDescription(Optional.ofNullable(oldValue).orElse(""));
            });
            Optional.ofNullable(diff.getExpectedResult()).ifPresent(value -> {
                final String oldValue = value.getOldValue();
                System.out.printf("For test case [%s] set expected result [%s]%n", testCaseId, oldValue);
                testCase.setExpectedResult(Optional.ofNullable(oldValue).orElse(""));
            });
            Optional.ofNullable(diff.getPrecondition()).ifPresent(value -> {
                final String oldValue = value.getOldValue();
                System.out.printf("For test case [%s] set precondition [%s]%n", testCaseId, oldValue);
                testCase.setPrecondition(Optional.ofNullable(oldValue).orElse(""));
            });
            service.update(testCaseId, testCase).execute();
        }
    }

    public static class RollbackCustomFieldDeleteAction implements AuditAction {

        @Override
        public boolean isApplicable(final TestCaseAuditType type, final TestCaseAuditEntryData data) {
            return TestCaseAuditType.DELETE.equals(type) && "test_case_custom_field".equals(data.getType());
        }

        @Override
        public void apply(final ServiceBuilder builder,
                          final Long testCaseId,
                          final TestCaseAuditType type,
                          final TestCaseAuditEntryData data) throws Exception {
            final TestCaseService service = builder.create(TestCaseService.class);
            final TestCaseAssociationDiff diff = (TestCaseAssociationDiff) data.getDiff();
            final List<CustomFieldValue> fields = executeRequest(service.getCustomFields(testCaseId));
            final Set<Long> ids = diff.getIds().getOldValue();
            System.out.printf("For test case [%s] add fields [%s]%n", testCaseId, ids);
            for (Long id : ids) {
                if (fields.stream().filter(field -> field.getId().equals(id)).findAny().isEmpty()) {
                    fields.add(new CustomFieldValue().setId(id));
                }
            }
            executeRequest(service.setCustomFields(testCaseId, fields));
        }
    }

    public static class RollbackCustomFieldInsertAction implements AuditAction {

        @Override
        public boolean isApplicable(final TestCaseAuditType type, final TestCaseAuditEntryData data) {
            return TestCaseAuditType.INSERT.equals(type) && "test_case_custom_field".equals(data.getType());
        }

        @Override
        public void apply(final ServiceBuilder builder,
                          final Long testCaseId,
                          final TestCaseAuditType type,
                          final TestCaseAuditEntryData data) throws Exception {
            final TestCaseService service = builder.create(TestCaseService.class);
            final TestCaseAssociationDiff diff = (TestCaseAssociationDiff) data.getDiff();
            final List<CustomFieldValue> fields = executeRequest(service.getCustomFields(testCaseId));
            final Set<Long> ids = diff.getIds().getNewValue();
            System.out.printf("For test case [%s] remove fields [%s]%n", testCaseId, ids);
            for (Long id : ids) {
                final Optional<CustomFieldValue> founded = fields.stream()
                        .filter(field -> field.getId().equals(id))
                        .findAny();
                founded.ifPresent(fields::remove);
            }
            executeRequest(service.setCustomFields(testCaseId, fields));
        }
    }

    public static class RollbackTagsDeleteAction implements AuditAction {

        @Override
        public boolean isApplicable(final TestCaseAuditType type, final TestCaseAuditEntryData data) {
            return TestCaseAuditType.DELETE.equals(type) && "test_case_test_tag".equals(data.getType());
        }

        @Override
        public void apply(final ServiceBuilder builder,
                          final Long testCaseId,
                          final TestCaseAuditType type,
                          final TestCaseAuditEntryData data) throws Exception {
            final TestCaseService service = builder.create(TestCaseService.class);
            final TestCaseAssociationDiff diff = (TestCaseAssociationDiff) data.getDiff();
            final TestCase testCase = executeRequest(service.findById(testCaseId));
            final Set<Long> idsToAdd = diff.getIds().getOldValue();
            System.out.printf("For test case [%s] add tags [%s]%n", testCaseId, idsToAdd);

            final Set<Long> newIds = testCase.getTags().stream()
                    .map(TestTag::getId).collect(Collectors.toSet());
            newIds.addAll(idsToAdd);
            testCase.setTags(newIds.stream().map(id -> new TestTag().setId(id)).collect(Collectors.toList()));
            executeRequest(service.update(testCaseId, null));
        }
    }

    public static class RollbackTagsInsertAction implements AuditAction {

        @Override
        public boolean isApplicable(final TestCaseAuditType type, final TestCaseAuditEntryData data) {
            return TestCaseAuditType.INSERT.equals(type) && "test_case_test_tag".equals(data.getType());
        }

        @Override
        public void apply(final ServiceBuilder builder,
                          final Long testCaseId,
                          final TestCaseAuditType type,
                          final TestCaseAuditEntryData data) throws Exception {
            final TestCaseService service = builder.create(TestCaseService.class);
            final TestCaseAssociationDiff diff = (TestCaseAssociationDiff) data.getDiff();
            final TestCase testCase = executeRequest(service.findById(testCaseId));
            final Set<Long> idsToRemove = diff.getIds().getNewValue();
            System.out.printf("For test case [%s] remove tags [%s]%n", testCaseId, idsToRemove);

            final Set<Long> newIds = testCase.getTags().stream()
                    .map(TestTag::getId).collect(Collectors.toSet());
            newIds.removeAll(idsToRemove);
            testCase.setTags(newIds.stream().map(id -> new TestTag().setId(id)).collect(Collectors.toList()));
            executeRequest(service.update(testCaseId, null));
        }
    }
}
