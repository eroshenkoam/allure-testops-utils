package io.github.eroshenkoam.allure.client.dto;

import io.github.eroshenkoam.allure.client.dto.textmarkup.TextMarkupDocument;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@Accessors(chain = true)
public class ScenarioStep implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long testCaseId;
    private Long attachmentId;
    private Long sharedStepId;
    private Long expectedResultId;

    private String body;
    private TextMarkupDocument bodyJson;

    private String expectedResult;
    private Boolean archived;

    private List<Long> children;

    private Long createdDate;
    private Long lastModifiedDate;
    private String createdBy;
    private String lastModifiedBy;

}
