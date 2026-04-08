package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author eroshenkoam (Artem Eroshenko).
 */
@Data
@Accessors(chain = true)
public class Account {

    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String password;
    private String username;

}
