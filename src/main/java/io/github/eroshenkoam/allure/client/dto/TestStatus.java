package io.github.eroshenkoam.allure.client.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.io.Serializable;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * @author eroshenkoam (Artem Eroshenko).
 */
public enum TestStatus implements Serializable {

    FAILED("failed"),
    BROKEN("broken"),
    PASSED("passed"),
    SKIPPED("skipped"),
    UNKNOWN("unknown");

    private static final long serialVersionUID = 1L;

    private final String value;

    TestStatus(final String v) {
        value = v;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static TestStatus fromValue(final String value) {
        if (Objects.isNull(value)) {
            return null;
        }
        return Stream.of(TestStatus.values())
                .filter(status -> status.value().equalsIgnoreCase(value))
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Could not find test status with value " + value));
    }

}
