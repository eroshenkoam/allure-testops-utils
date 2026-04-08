package io.github.eroshenkoam.allure.client.dto;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * @author eroshenkoam (Artem Eroshenko).
 */
public enum TestCaseRelationType {

    RELATES_TO("relates to"),

    CLONES("clones"),
    IS_CLONED_BY("is cloned by"),

    DUPLICATES("duplicates"),
    IS_DUPLICATED_BY("is duplicated by"),

    AUTOMATES("automates"),
    IS_AUTOMATED_BY("is automated by");

    private static final long serialVersionUID = 1L;

    private final String value;

    TestCaseRelationType(final String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

}
