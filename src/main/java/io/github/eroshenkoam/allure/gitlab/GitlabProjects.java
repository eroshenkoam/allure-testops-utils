package io.github.eroshenkoam.allure.gitlab;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class GitlabProjects implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String path;

}
