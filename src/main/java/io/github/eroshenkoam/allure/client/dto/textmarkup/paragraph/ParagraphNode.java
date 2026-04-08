package io.github.eroshenkoam.allure.client.dto.textmarkup.paragraph;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;

/**
 * @author vbragin
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type",
        defaultImpl = UnknownParagraphNode.class
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = LineBreakParagraphNode.class, name = "lineBreak"),
        @JsonSubTypes.Type(value = TextParagraphNode.class, name = "text"),
})
public interface ParagraphNode extends Serializable {

}
