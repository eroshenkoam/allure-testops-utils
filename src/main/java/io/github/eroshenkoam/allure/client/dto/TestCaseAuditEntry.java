package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@Accessors(chain = true)
public class TestCaseAuditEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long timestamp;
    private String username;
    private TestCaseAuditType actionType;
    private List<TestCaseAuditEntryData> data;

}
