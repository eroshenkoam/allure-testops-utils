package io.github.eroshenkoam.allure.client.retrofit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eroshenkoam.allure.client.ServerClientException;
import io.github.eroshenkoam.allure.client.dto.Token;
import okhttp3.FormBody;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Objects;

/**
 * @author eroshenkoam (Artem Eroshenko).
 */
public abstract class BaseAuthenticator implements Interceptor {

    private static final String AUTHORIZATION_HEADER_NAME = "Authorization";

    private static final int UNAUTHORIZED_CODE = 401;

    private static final String TOKEN_URL = "/api/uaa/oauth/token";

    private Token token;

    @Override
    public Response intercept(final Chain chain) throws IOException {
        if (Objects.isNull(token)) {
            token = createToken(chain, newTokenForm());
        }

        final Response response = executeRequest(chain);
        if (response.code() == UNAUTHORIZED_CODE) {
            response.close();
            token = createToken(chain, newTokenForm());
            return executeRequest(chain);
        }

        return response;
    }

    private Response executeRequest(final Chain chain) throws IOException {
        final Request request = chain.request().newBuilder()
                .addHeader(AUTHORIZATION_HEADER_NAME, String.format("Bearer %s", token.getAccessToken()))
                .build();
        return chain.proceed(request);
    }

    private Token createToken(@Nonnull final Chain chain, @Nonnull final FormBody form) throws IOException {
        final Request jwtTokenRequest = createTokenRequest(chain.request(), form);
        try (Response jwtTokenResponse = chain.proceed(jwtTokenRequest)) {
            try (ResponseBody body = jwtTokenResponse.body()) {
                if (Objects.isNull(body)) {
                    throw new ServerClientException("Could not get token: empty response body");
                }
                if (!jwtTokenResponse.isSuccessful()) {
                    throw new ServerClientException("get token", body.string());
                }
                return new ObjectMapper().readValue(body.string(), Token.class);
            }
        }
    }

    private Request createTokenRequest(@Nonnull final Request request,
                                       @Nonnull final FormBody body) {
        return request.newBuilder()
                .url(Objects.requireNonNull(request.url().resolve(TOKEN_URL), "Could not resolve token url"))
                .header(AUTHORIZATION_HEADER_NAME, "Basic YWNtZTphY21lc2VjcmV0")
                .post(body)
                .build();
    }

    public abstract FormBody newTokenForm();


}
