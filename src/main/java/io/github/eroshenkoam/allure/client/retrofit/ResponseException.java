package io.github.eroshenkoam.allure.client.retrofit;

public class ResponseException extends RuntimeException {

    public ResponseException(final Exception e) {
        super(e);
    }

}
