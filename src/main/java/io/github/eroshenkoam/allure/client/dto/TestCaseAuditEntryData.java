package io.github.eroshenkoam.allure.client.dto;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.github.eroshenkoam.allure.client.audit.Diff;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class TestCaseAuditEntryData implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
            property = "type"
    )
    private Diff diff;
    private String type;


}
