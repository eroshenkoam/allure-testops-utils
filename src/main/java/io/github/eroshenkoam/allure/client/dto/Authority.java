package io.github.eroshenkoam.allure.client.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * @author vitalybragin
 */
public enum Authority implements Serializable {

    ROLE_ADMIN("admin"),
    ROLE_USER("user"),
    ROLE_GUEST("guest");

    @Serial
    private static final long serialVersionUID = 1L;

    private final String value;

    Authority(final String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static Authority fromValue(final String value) {
        if (Objects.isNull(value)) {
            return null;
        }
        return Stream.of(values())
                .filter(v -> v.value().equalsIgnoreCase(value))
                .findAny()
                .orElseThrow(() -> new IllegalStateException("Could not find type with value " + value));
    }
}
