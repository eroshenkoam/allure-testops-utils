package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

/**
 * @author charlie (Dmitry Baev).
 */
@Data
@Accessors(chain = true)
public class CreateLaunchEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    protected String name;
    protected Long projectId;

    protected Long testPackId;
    protected String testPackExternalId;
    protected String testPackName;

    protected List<String> tags;
}
