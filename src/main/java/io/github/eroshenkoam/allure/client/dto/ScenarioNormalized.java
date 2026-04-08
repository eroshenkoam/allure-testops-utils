package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

@Data
@Accessors(chain = true)
public class ScenarioNormalized implements Serializable {

    private ScenarioStep root;

    private Map<Long, ScenarioStep> scenarioSteps;
    private Map<Long, ScenarioAttachment> attachments;

    private Map<Long, ScenarioStep> sharedSteps;
    private Map<Long, ScenarioStep> sharedStepScenarioSteps;
    private Map<Long, ScenarioAttachment> sharedStepAttachments;

}
