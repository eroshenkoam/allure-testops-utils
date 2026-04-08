package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@Accessors(chain = true)
public class TestCasePatch implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private String fullName;

    private String description;
    private String precondition;
    private String expectedResult;

    private Boolean deleted;
    private Boolean automated;
    private Boolean external;

    private Long testLayerId;
    private Long statusId;
    private Long workflowId;

    private TestCaseScenario scenario;

    private List<TestTag> tags;
    private List<ExternalLink> links;

    private List<CustomFieldValue> customFields;
    private List<Member> members;


}
