package io.github.eroshenkoam.allure.client.retrofit;

import okhttp3.FormBody;

import javax.annotation.Nonnull;

/**
 * @author Artem Eroshenko.
 */
public class LoginPasswordAuthenticator extends BaseAuthenticator {

    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";

    private final String username;
    private final String password;

    public LoginPasswordAuthenticator(@Nonnull final String username, @Nonnull final String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public FormBody newTokenForm() {
        return new FormBody.Builder()
                .add(USERNAME, username)
                .add(PASSWORD, password)
                .add("grant_type", PASSWORD)
                .add("scope", "openid")
                .build();
    }

}
