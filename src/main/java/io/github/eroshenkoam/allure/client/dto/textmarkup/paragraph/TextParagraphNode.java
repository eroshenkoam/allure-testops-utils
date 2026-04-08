package io.github.eroshenkoam.allure.client.dto.textmarkup.paragraph;

import io.github.eroshenkoam.allure.client.dto.textmarkup.textmark.TextMark;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.util.List;

/**
 * @author vbragin
 */
@Data
@Accessors(chain = true)
public class TextParagraphNode implements ParagraphNode {

    @Serial
    private static final long serialVersionUID = 1L;

    private String text;
    private List<TextMark> marks;

}
