package io.github.eroshenkoam.allure.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.eroshenkoam.allure.client.dto.textmarkup.TextMarkupDocument;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScenarioStepUpdate implements Serializable {

    private static final long serialVersionUID = 1L;

    private String body;
    private TextMarkupDocument bodyJson;

    private String expectedResult;
    private Long attachmentId;
    private Long sharedStepId;


}
