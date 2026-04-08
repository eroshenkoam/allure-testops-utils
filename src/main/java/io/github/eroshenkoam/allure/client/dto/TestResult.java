package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author eroshenkoam (Artem Eroshenko).
 */
@Data
@Accessors(chain = true)
public class TestResult {

    private static final long serialVersionUID = 1L;

    private Long id;

    private Long projectId;
    private Long launchId;
    private Long testCaseId;

    private String historyKey;
    private String scenarioKey;

    private String name;
    private String fullName;
    private String description;
    private String descriptionHtml;

    private String precondition;
    private String preconditionHtml;

    private String expectedResult;
    private String expectedResultHtml;

    private String status;
    private String message;
    private String trace;

    private Long start;
    private Long stop;
    private Long duration;


}
