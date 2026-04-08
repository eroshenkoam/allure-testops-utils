package io.github.eroshenkoam.allure.client.audit;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @author charlie (Dmitry Baev).
 */
@Data
@Accessors(chain = true)
public class TestCaseDiff implements Diff, Serializable {

    private static final long serialVersionUID = 1L;

    @JsonAlias("project_id")
    private DiffValueChange<Long> projectId;

    private DiffValueChange<String> name;

    private DiffValueChange<Boolean> automated;
    private DiffValueChange<Boolean> deleted;

    private DiffValueChange<String> description;
    @JsonAlias("description_html")
    private DiffValueChange<String> descriptionHtml;

    private DiffValueChange<String> precondition;
    @JsonAlias("precondition_html")
    private DiffValueChange<String> preconditionHtml;

    @JsonAlias("expected_result")
    private DiffValueChange<String> expectedResult;
    @JsonAlias("expected_result_html")
    private DiffValueChange<String> expectedResultHtml;

    @JsonAlias("status_id")
    private DiffValueChange<Long> statusId;

    @JsonAlias("workflow_id")
    private DiffValueChange<Long> workflowId;

    @JsonAlias("test_layer_id")
    private DiffValueChange<Long> testLayerId;

}
