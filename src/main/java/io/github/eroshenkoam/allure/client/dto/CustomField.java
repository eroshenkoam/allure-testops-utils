package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @author eroshenkoam (Artem Eroshenko).
 */
@Data
@Accessors(chain = true)
public class CustomField implements Serializable {

    private static final long serialVersionUID = 1L;

    protected Long id;
    protected String name;

    protected Long projectId;

}
