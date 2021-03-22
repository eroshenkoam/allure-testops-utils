package io.github.eroshenkoam.allure.crowd;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@Accessors(chain = true)
public class CrowdGroups implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<CrowdGroup> groups;

}
