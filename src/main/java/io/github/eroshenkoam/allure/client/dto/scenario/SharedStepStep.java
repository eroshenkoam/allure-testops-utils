package io.github.eroshenkoam.allure.client.dto.scenario;

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
public class SharedStepStep implements ScenarioStep {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long sharedStepId;

    private BodyStep stepData;
}
