package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * @author eroshenkoam (Artem Eroshenko).
 */
@Data
@Accessors(chain = true)
public class TestCaseStep implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private String keyword;
    private String expectedResult;

    private List<TestCaseStep> steps;
    private List<TestCaseAttachment> attachments;

}
