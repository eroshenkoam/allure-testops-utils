package io.github.eroshenkoam.allure.crowd;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class CrowdGroup implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;

}
