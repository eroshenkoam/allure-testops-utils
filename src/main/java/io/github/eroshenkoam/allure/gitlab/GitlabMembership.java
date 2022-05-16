package io.github.eroshenkoam.allure.gitlab;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class GitlabMembership implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("source_type")
    private String sourceType;
    @JsonProperty("source_name")
    private String sourceName;

}
