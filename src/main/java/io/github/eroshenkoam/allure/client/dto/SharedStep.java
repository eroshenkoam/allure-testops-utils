package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class SharedStep implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long projectId;

    private String name;

    private Boolean archived;

    private Long stepsCount;
    private Long attachmentsCount;
    private Long testCasesCount;

    private Long createdDate;
    private Long lastModifiedDate;
    private String createdBy;
    private String lastModifiedBy;

}
