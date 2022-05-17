package io.github.eroshenkoam.allure.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class TestCaseAttachmentDto implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private Long contentLength;
    private String contentType;

}
