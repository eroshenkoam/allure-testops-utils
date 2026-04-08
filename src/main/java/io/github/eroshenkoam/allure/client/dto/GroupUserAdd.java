package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.List;

@Data
@Accessors(chain = true)
public class GroupUserAdd implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<String> usernames;

}
