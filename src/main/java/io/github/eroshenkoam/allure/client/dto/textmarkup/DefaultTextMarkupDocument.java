package io.github.eroshenkoam.allure.client.dto.textmarkup;

import io.github.eroshenkoam.allure.client.dto.textmarkup.document.DocumentNode;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.util.List;

/**
 * @author vbragin
 */
@Data
@Accessors(chain = true)
public class DefaultTextMarkupDocument implements TextMarkupDocument {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<DocumentNode> content;

}
