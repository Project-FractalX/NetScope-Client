package org.fractalx.netscope.client.exception;

import io.grpc.StatusRuntimeException;

/**
 * Wraps a gRPC {@link StatusRuntimeException} so that consuming code
 * does not need to depend on the gRPC API surface directly.
 */
public class NetScopeRemoteException extends NetScopeClientException {

    private final StatusRuntimeException grpcCause;

    public NetScopeRemoteException(String message, StatusRuntimeException cause) {
        super(message, cause);
        this.grpcCause = cause;
    }

    public StatusRuntimeException getGrpcCause() {
        return grpcCause;
    }

    public io.grpc.Status getStatus() {
        return grpcCause.getStatus();
    }
}
