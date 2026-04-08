package io.github.eroshenkoam.allure.client.dto.textmarkup;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;

/**
 * @author charlie (Dmitry Baev).
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type",
        defaultImpl = UnknownTextMarkupDocument.class
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = DefaultTextMarkupDocument.class, name = "doc"),
})
public interface TextMarkupDocument extends Serializable {
}
