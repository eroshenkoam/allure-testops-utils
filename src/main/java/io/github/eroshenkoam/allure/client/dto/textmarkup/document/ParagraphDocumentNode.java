package io.github.eroshenkoam.allure.client.dto.textmarkup.document;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.eroshenkoam.allure.client.dto.textmarkup.paragraph.ParagraphNode;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * @author vbragin
 */
@Data
@Accessors(chain = true)
public class ParagraphDocumentNode implements DocumentNode {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<ParagraphNode> content;
    private Attrs attrs;

    @Data
    @Accessors(chain = true)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Attrs implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

    }
}
