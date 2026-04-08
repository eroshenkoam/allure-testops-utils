package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @author charlie (Dmitry Baev).
 */
@Data
@Accessors(chain = true)
public class Launch implements Serializable {

    private static final long serialVersionUID = 1L;

    protected Long id;
    protected Long projectId;
    protected Long testPackId;

    protected String name;
    protected Long buildOrder;

    protected Long createdDate;
    protected Long lastModifiedDate;
    protected String createdBy;
    protected String lastModifiedBy;

}
