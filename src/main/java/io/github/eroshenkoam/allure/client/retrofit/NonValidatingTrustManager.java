package io.github.eroshenkoam.allure.client.retrofit;

import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * @author charlie (Dmitry Baev).
 */
public class NonValidatingTrustManager implements X509TrustManager {

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }

    @Override
    public void checkClientTrusted(final X509Certificate[] certs, final String authType) {
        //do nothing
    }

    @Override
    public void checkServerTrusted(final X509Certificate[] certs, final String authType) {
        //do nothing
    }
}
