package io.github.eroshenkoam.allure.client.retrofit;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class RetryInterceptor implements Interceptor {

    private static final int UNAUTHORIZED_CODE = 401;

    private final long delayInMillis;
    private final int maxRetries;

    public RetryInterceptor(final int maxRetries, final long delayInMillis) {
        this.delayInMillis = delayInMillis;
        this.maxRetries = maxRetries;
    }

    @Override
    public Response intercept(final Chain chain) throws IOException {
        final Request request = chain.request();

        int tryCount = 0;
        while (true) {
            tryCount += 1;
            try {
                final Response response = chain.proceed(request);
                if (response.isSuccessful() || response.code() == UNAUTHORIZED_CODE || tryCount >= maxRetries) {
                    return response;
                } else {
                    response.close();
                }
                response.close();
            } catch (IOException e) {
                if (tryCount >= maxRetries) {
                    throw e;
                }
            }
            try {
                Thread.sleep(delayInMillis);
            } catch (InterruptedException e) {
                //do nothing
            }
        }
    }

}
