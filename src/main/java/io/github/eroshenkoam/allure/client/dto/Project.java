package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @author charlie (Dmitry Baev).
 */
@Data
@Accessors(chain = true)
public class Project implements Serializable {

    private static final long serialVersionUID = 1L;

    protected Long id;

    protected String name;
    protected String description;
    protected String descriptionHtml;

    protected Long createdDate;
    protected Long lastModifiedDate;
    protected String createdBy;
    protected String lastModifiedBy;

}
