package io.github.eroshenkoam.allure.client.dto;

import io.github.eroshenkoam.allure.client.dto.textmarkup.TextMarkupDocument;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class ScenarioStepCreate implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long parentId;
    private Long testCaseId;

    private String body;
    private TextMarkupDocument bodyJson;

    private String expectedResult;
    private Long attachmentId;
    private Long sharedStepId;

}
