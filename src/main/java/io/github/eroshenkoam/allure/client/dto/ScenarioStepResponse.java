package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class ScenarioStepResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long createdStepId;
    private ScenarioNormalized scenario;

}
