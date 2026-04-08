package io.github.eroshenkoam.allure.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;

/**
 * @author charlie (Dmitry Baev).
 */
@Data
@Accessors(chain = true)
public class Token implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("access_token")
    protected String accessToken;

    @JsonProperty("token_type")
    protected String tokenType;

    @JsonProperty("refresh_token")
    protected String refreshToken;

    @JsonProperty("expires_in")
    protected String expiresIn;

    protected String scope;

    protected String jti;

}
