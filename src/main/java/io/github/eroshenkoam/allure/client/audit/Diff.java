package io.github.eroshenkoam.allure.client.audit;

import com.fasterxml.jackson.annotation.JsonSubTypes;

/**
 * @author charlie (Dmitry Baev).
 */
@JsonSubTypes({
        @JsonSubTypes.Type(value = TestCaseDiff.class, name = "test_case"),
        @JsonSubTypes.Type(value = TestCaseAssociationDiff.class, name = "test_case_issue"),
        @JsonSubTypes.Type(value = TestCaseAssociationDiff.class, name = "test_case_members"),
        @JsonSubTypes.Type(value = TestCaseAssociationDiff.class, name = "test_case_test_tag"),
        @JsonSubTypes.Type(value = TestCaseAssociationDiff.class, name = "test_case_custom_field"),
        @JsonSubTypes.Type(value = TestCaseAssociationDiff.class, name = "test_case_test_key"),
        @JsonSubTypes.Type(value = TestCaseAssociationDiff.class, name = "test_case_defect"),
})
public interface Diff {
}
