package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

@Data
@Accessors(chain = true)
public class GroupUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private String username;

}
