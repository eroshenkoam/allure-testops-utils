package io.github.eroshenkoam.allure.client.dto.scenario;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author vbragin
 */
@Data
@Accessors(chain = true)
public class SharedStepScenario implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private List<ScenarioStep> steps = new ArrayList<>();
}
