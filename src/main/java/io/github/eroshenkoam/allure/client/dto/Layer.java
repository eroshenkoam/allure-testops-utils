package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @author charlie (Dmitry Baev).
 */
@Data
@Accessors(chain = true)
public class Layer implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;

}
