package io.github.eroshenkoam.allure.client;

/**
 * @author Vitaly Bragin.
 */
public class ServerClientException extends RuntimeException {

    public ServerClientException(final String message) {
        super(message);
    }

    public ServerClientException(final String operationDescription, final String message, final Throwable e) {
        super(getFormattedMessage(operationDescription, message), e);
    }

    public ServerClientException(final String operationDescription, final Throwable e) {
        super(String.format("Could not %s", operationDescription), e);
    }

    public ServerClientException(final String operationDescription, final String message) {
        super(getFormattedMessage(operationDescription, message));
    }

    protected static String getFormattedMessage(final String operationDescription, final String message) {
        return String.format("Could not %s: %s", operationDescription, message);
    }

}
