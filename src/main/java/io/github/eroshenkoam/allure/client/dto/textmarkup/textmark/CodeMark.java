package io.github.eroshenkoam.allure.client.dto.textmarkup.textmark;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;

/**
 * @author vbragin
 */
@Data
@Accessors(chain = true)
public class CodeMark implements TextMark {

    @Serial
    private static final long serialVersionUID = 1L;
}
