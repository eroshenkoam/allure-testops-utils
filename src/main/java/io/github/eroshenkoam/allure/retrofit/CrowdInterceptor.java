package io.github.eroshenkoam.allure.retrofit;

import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class CrowdInterceptor implements Interceptor {

    private String credentials;

    public CrowdInterceptor(String user, String password) {
        this.credentials = Credentials.basic(user, password);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Request authenticatedRequest = request.newBuilder()
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Authorization", credentials)
                .build();
        return chain.proceed(authenticatedRequest);
    }

}