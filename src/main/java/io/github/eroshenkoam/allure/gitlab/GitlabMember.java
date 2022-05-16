package io.github.eroshenkoam.allure.gitlab;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class GitlabMember implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;

}
