package io.github.eroshenkoam.allure.client.dto.textmarkup.document;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;

/**
 * @author vbragin
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type",
        defaultImpl = UnknownDocumentNode.class
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ParagraphDocumentNode.class, name = "paragraph"),
        @JsonSubTypes.Type(value = CodeBlockDocumentNode.class, name = "code_block"),
})
public interface DocumentNode extends Serializable {
}
