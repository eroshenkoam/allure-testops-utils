package io.github.eroshenkoam.allure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.eroshenkoam.allure.client.retrofit.LoginPasswordAuthenticator;
import io.github.eroshenkoam.allure.client.retrofit.NonValidatingTrustManager;
import io.github.eroshenkoam.allure.client.retrofit.ResponseCallAdapterFactory;
import io.github.eroshenkoam.allure.client.retrofit.RetryInterceptor;
import io.github.eroshenkoam.allure.client.retrofit.TokenAuthenticator;
import okhttp3.Dispatcher;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

/**
 * @author eroshenkoam (Artem Eroshenko).
 */
@SuppressWarnings({
        "PMD.AvoidFieldNameMatchingTypeName",
        "ClassDataAbstractionCoupling"
})
public class ServiceBuilder {

    private final OkHttpClient.Builder clientBuilder;
    private final Retrofit.Builder retrofitBuilder;

    public ServiceBuilder(final String baseUrl) {
        this.clientBuilder = new OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.MINUTES)
                .writeTimeout(5, TimeUnit.MINUTES)
                .readTimeout(10, TimeUnit.MINUTES);
        this.retrofitBuilder = new Retrofit.Builder()
                .baseUrl(baseUrl);
    }

    public ServiceBuilder authToken(final String token) {
        this.clientBuilder.addInterceptor(new TokenAuthenticator(token));
        return this;
    }

    public ServiceBuilder authBasic(final String username, final String password) {
        this.clientBuilder.addInterceptor(new LoginPasswordAuthenticator(username, password));
        return this;
    }

    public ServiceBuilder retryCount(final int count) {
        return this.retryCount(count, TimeUnit.SECONDS.toMillis(1));
    }

    public ServiceBuilder retryCount(final int count, final long delayInMillis) {
        if (count > 0) {
            this.clientBuilder.addInterceptor(new RetryInterceptor(count, delayInMillis));
        }
        return this;
    }

    public ServiceBuilder withInterceptor(final Interceptor interceptor) {
        this.clientBuilder.addInterceptor(interceptor);
        return this;
    }

    public ServiceBuilder withLog(final boolean isDebug) {
        if (!isDebug) {
            return this;
        }
        final HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(HttpLoggingInterceptor.Level.BASIC);
        this.clientBuilder.addInterceptor(logging);
        return this;
    }

    public ServiceBuilder withDispatcher(final Dispatcher dispatcher) {
        this.clientBuilder.dispatcher(dispatcher);
        return this;
    }

    public ServiceBuilder insecure(final boolean insecure) {
        if (insecure) {
            turnOffSslValidation(clientBuilder);
        }
        return this;
    }

    public ServiceBuilder writeTimeout(final long timeunit, final TimeUnit unit) {
        this.clientBuilder.writeTimeout(timeunit, unit);
        return this;
    }

    public ServiceBuilder readTimeout(final long timeunit, final TimeUnit unit) {
        this.clientBuilder.readTimeout(timeunit, unit);
        return this;
    }

    public ServiceBuilder connectTimeout(final long timeunit, final TimeUnit unit) {
        this.clientBuilder.connectTimeout(timeunit, unit);
        return this;
    }

    public ServiceBuilder withRetryOnConnectionFailure() {
        this.clientBuilder.retryOnConnectionFailure(true);
        return this;
    }

    public <T> T create(final Class<T> clazz) {
        final OkHttpClient client = clientBuilder
                .build();
        final ObjectMapper mapper = new ObjectMapper().disable(FAIL_ON_UNKNOWN_PROPERTIES);
        final Retrofit retrofit = retrofitBuilder
                .addConverterFactory(JacksonConverterFactory.create(mapper))
                .addCallAdapterFactory(new ResponseCallAdapterFactory<>())
                .client(client)
                .build();
        return retrofit.create(clazz);
    }

    @SuppressWarnings("PMD.EmptyCatchBlock")
    private static void turnOffSslValidation(final OkHttpClient.Builder builder) {
        try {
            final X509TrustManager trustManager = new NonValidatingTrustManager();
            final SSLContext ctx = SSLContext.getInstance("TLS"); // or "SSL" ?
            ctx.init(null, new TrustManager[]{trustManager}, null);
            final SSLSocketFactory sslSocketFactory = ctx.getSocketFactory();
            builder.sslSocketFactory(sslSocketFactory, trustManager);
            builder.hostnameVerifier((hostname, session) -> true);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            // do nothing
        }
    }
}
