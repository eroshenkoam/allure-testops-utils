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
public class TestResultStep implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private String keyword;

    private Long start;
    private Long stop;
    private Long duration;

    private String message;
    private String trace;

    private TestStatus status;

    private List<TestResultStep> steps;
    private List<TestResultAttachment> attachments;

}
