package io.github.eroshenkoam.allure.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@Accessors(chain = true)
public class TestCaseStepDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private List<TestCaseStepDto> steps;
    private List<TestCaseAttachmentDto> attachments;

}
