package org.fractalx.netscope.client.exception;

public class NetScopeClientException extends RuntimeException {

    public NetScopeClientException(String message) {
        super(message);
    }

    public NetScopeClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
