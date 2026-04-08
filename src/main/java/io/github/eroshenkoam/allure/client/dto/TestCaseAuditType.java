package io.github.eroshenkoam.allure.client.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;
import java.util.stream.Stream;

public enum TestCaseAuditType {

    INSERT("insert"),
    UPDATE("update"),
    DELETE("delete");

    private static final long serialVersionUID = 1L;

    private final String value;

    TestCaseAuditType(final String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static TestCaseAuditType fromValue(final String value) {
        if (Objects.isNull(value)) {
            return null;
        }
        return Stream.of(values())
                .filter(t -> t.value().equalsIgnoreCase(value))
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Could not find audit action type "
                        + "with value " + value));
    }

}
