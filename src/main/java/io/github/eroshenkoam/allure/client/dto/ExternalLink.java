package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @author charlie (Dmitry Baev).
 */
@Data
@Accessors(chain = true)
public class ExternalLink implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private String url;
    private String type;

}
