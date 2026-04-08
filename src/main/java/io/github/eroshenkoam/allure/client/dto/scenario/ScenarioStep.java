package io.github.eroshenkoam.allure.client.dto.scenario;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.io.Serializable;

/**
 * @author vbragin
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type",
        defaultImpl = UnknownStep.class
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = BodyStep.class, name = "body"),
        @JsonSubTypes.Type(value = AttachmentStep.class, name = "attachment"),
        @JsonSubTypes.Type(value = SharedStepStep.class, name = "shared"),
        @JsonSubTypes.Type(value = ExpectedBodyStep.class, name = "expected_body"),
})
public interface ScenarioStep extends Serializable {

}
