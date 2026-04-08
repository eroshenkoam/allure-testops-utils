package io.github.eroshenkoam.allure.client.dto.textmarkup;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;

/**
 * @author vbragin
 */
@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class UnknownTextMarkupDocument implements TextMarkupDocument {

    @Serial
    private static final long serialVersionUID = 1L;
}
