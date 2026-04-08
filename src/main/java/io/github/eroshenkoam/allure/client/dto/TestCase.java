package io.github.eroshenkoam.allure.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * @author eroshenkoam (Artem Eroshenko).
 */
@Data
@Accessors(chain = true)
public class TestCase implements Serializable {

    private static final long serialVersionUID = 1L;

    protected Long id;
    protected Long projectId;

    protected String name;
    protected String fullName;

    protected String description;
    protected String precondition;
    protected String expectedResult;

    protected Boolean automated;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    protected Boolean external;
    protected Boolean deleted;
    protected Boolean editable;

    protected List<ExternalLink> links;

    protected String hash;

    protected String type;

    protected Layer layer;
    protected Status status;
    protected Workflow workflow;

    private List<TestTag> tags;

    private Long createdDate;
    private Long lastModifiedDate;
    private String createdBy;
    private String lastModifiedBy;

}
