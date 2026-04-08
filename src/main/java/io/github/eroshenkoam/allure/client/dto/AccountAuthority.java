package io.github.eroshenkoam.allure.client.dto;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author eroshenkoam (Artem Eroshenko).
 */
@Data
@Accessors(chain = true)
public class AccountAuthority {

    private final Authority globalRole;

}
