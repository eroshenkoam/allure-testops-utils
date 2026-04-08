package io.github.eroshenkoam.allure.client.dto.scenario;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.eroshenkoam.allure.client.dto.textmarkup.TextMarkupDocument;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;

/**
 * @author vbragin
 */
@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExpectedBodyStep implements ScenarioStep {

    @Serial
    private static final long serialVersionUID = 1L;

    private String body;
    private TextMarkupDocument bodyJson;
}
