package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @author eroshenkoam (Artem Eroshenko).
 */
@Data
@Accessors(chain = true)
public class Issue implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;

    /**
     * @deprecated use integrationId instead
     */
    @Deprecated
    private Long trackerId;
    private Long integrationId;
    private String url;
    private Boolean closed;

}
