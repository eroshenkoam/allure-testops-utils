package io.github.eroshenkoam.allure.client.retrofit;

import okhttp3.FormBody;

/**
 * @author eroshenkoam (Artem Eroshenko).
 */
public class TokenAuthenticator extends BaseAuthenticator {

    private static final String TOKEN = "token";

    private final String token;

    public TokenAuthenticator(final String token) {
        this.token = token;
    }

    @Override
    public FormBody newTokenForm() {
        return new FormBody.Builder()
                .add(TOKEN, token)
                .add("scope", "openid")
                .add("grant_type", "apitoken")
                .build();
    }

}
