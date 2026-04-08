package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @author eroshenkoam (Artem Eroshenko).
 */
@Data
@Accessors(chain = true)
public class SharedStepAttachment implements Serializable {

    private static final long serialVersionUID = 1L;

    private String entity;
    private Long id;
    private String name;
    private String contentType;
    private Long contentLength;
    private Boolean missed;

}
