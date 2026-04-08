package io.github.eroshenkoam.allure.client.dto.textmarkup.textmark;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;

/**
 * @author vbragin
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type",
        defaultImpl = UnknownMark.class
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = CodeMark.class, name = "code"),
        @JsonSubTypes.Type(value = BoldMark.class, name = "bold"),
        @JsonSubTypes.Type(value = ItalicMark.class, name = "italic"),
        @JsonSubTypes.Type(value = UnderlineMark.class, name = "underline"),
        @JsonSubTypes.Type(value = StrikeMark.class, name = "strike"),
        @JsonSubTypes.Type(value = LinkMark.class, name = "link"),
        @JsonSubTypes.Type(value = TextColorMark.class, name = "text_color"),
        @JsonSubTypes.Type(value = TextFillMark.class, name = "text_fill")
})
public interface TextMark extends Serializable {
}
