package org.fractalx.netscope.client.grpc.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class NetScopeServiceGrpc {

  private NetScopeServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "netscope.NetScopeService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<org.fractalx.netscope.client.grpc.proto.InvokeRequest,
      org.fractalx.netscope.client.grpc.proto.InvokeResponse> getInvokeMethodMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "InvokeMethod",
      requestType = org.fractalx.netscope.client.grpc.proto.InvokeRequest.class,
      responseType = org.fractalx.netscope.client.grpc.proto.InvokeResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<org.fractalx.netscope.client.grpc.proto.InvokeRequest,
      org.fractalx.netscope.client.grpc.proto.InvokeResponse> getInvokeMethodMethod() {
    io.grpc.MethodDescriptor<org.fractalx.netscope.client.grpc.proto.InvokeRequest, org.fractalx.netscope.client.grpc.proto.InvokeResponse> getInvokeMethodMethod;
    if ((getInvokeMethodMethod = NetScopeServiceGrpc.getInvokeMethodMethod) == null) {
      synchronized (NetScopeServiceGrpc.class) {
        if ((getInvokeMethodMethod = NetScopeServiceGrpc.getInvokeMethodMethod) == null) {
          NetScopeServiceGrpc.getInvokeMethodMethod = getInvokeMethodMethod =
              io.grpc.MethodDescriptor.<org.fractalx.netscope.client.grpc.proto.InvokeRequest, org.fractalx.netscope.client.grpc.proto.InvokeResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "InvokeMethod"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.fractalx.netscope.client.grpc.proto.InvokeRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.fractalx.netscope.client.grpc.proto.InvokeResponse.getDefaultInstance()))
              .setSchemaDescriptor(new NetScopeServiceMethodDescriptorSupplier("InvokeMethod"))
              .build();
        }
      }
    }
    return getInvokeMethodMethod;
  }

  private static volatile io.grpc.MethodDescriptor<org.fractalx.netscope.client.grpc.proto.SetAttributeRequest,
      org.fractalx.netscope.client.grpc.proto.SetAttributeResponse> getSetAttributeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "SetAttribute",
      requestType = org.fractalx.netscope.client.grpc.proto.SetAttributeRequest.class,
      responseType = org.fractalx.netscope.client.grpc.proto.SetAttributeResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<org.fractalx.netscope.client.grpc.proto.SetAttributeRequest,
      org.fractalx.netscope.client.grpc.proto.SetAttributeResponse> getSetAttributeMethod() {
    io.grpc.MethodDescriptor<org.fractalx.netscope.client.grpc.proto.SetAttributeRequest, org.fractalx.netscope.client.grpc.proto.SetAttributeResponse> getSetAttributeMethod;
    if ((getSetAttributeMethod = NetScopeServiceGrpc.getSetAttributeMethod) == null) {
      synchronized (NetScopeServiceGrpc.class) {
        if ((getSetAttributeMethod = NetScopeServiceGrpc.getSetAttributeMethod) == null) {
          NetScopeServiceGrpc.getSetAttributeMethod = getSetAttributeMethod =
              io.grpc.MethodDescriptor.<org.fractalx.netscope.client.grpc.proto.SetAttributeRequest, org.fractalx.netscope.client.grpc.proto.SetAttributeResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "SetAttribute"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.fractalx.netscope.client.grpc.proto.SetAttributeRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.fractalx.netscope.client.grpc.proto.SetAttributeResponse.getDefaultInstance()))
              .setSchemaDescriptor(new NetScopeServiceMethodDescriptorSupplier("SetAttribute"))
              .build();
        }
      }
    }
    return getSetAttributeMethod;
  }

  private static volatile io.grpc.MethodDescriptor<org.fractalx.netscope.client.grpc.proto.DocsRequest,
      org.fractalx.netscope.client.grpc.proto.DocsResponse> getGetDocsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetDocs",
      requestType = org.fractalx.netscope.client.grpc.proto.DocsRequest.class,
      responseType = org.fractalx.netscope.client.grpc.proto.DocsResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<org.fractalx.netscope.client.grpc.proto.DocsRequest,
      org.fractalx.netscope.client.grpc.proto.DocsResponse> getGetDocsMethod() {
    io.grpc.MethodDescriptor<org.fractalx.netscope.client.grpc.proto.DocsRequest, org.fractalx.netscope.client.grpc.proto.DocsResponse> getGetDocsMethod;
    if ((getGetDocsMethod = NetScopeServiceGrpc.getGetDocsMethod) == null) {
      synchronized (NetScopeServiceGrpc.class) {
        if ((getGetDocsMethod = NetScopeServiceGrpc.getGetDocsMethod) == null) {
          NetScopeServiceGrpc.getGetDocsMethod = getGetDocsMethod =
              io.grpc.MethodDescriptor.<org.fractalx.netscope.client.grpc.proto.DocsRequest, org.fractalx.netscope.client.grpc.proto.DocsResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetDocs"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.fractalx.netscope.client.grpc.proto.DocsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.fractalx.netscope.client.grpc.proto.DocsResponse.getDefaultInstance()))
              .setSchemaDescriptor(new NetScopeServiceMethodDescriptorSupplier("GetDocs"))
              .build();
        }
      }
    }
    return getGetDocsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<org.fractalx.netscope.client.grpc.proto.InvokeRequest,
      org.fractalx.netscope.client.grpc.proto.InvokeResponse> getInvokeMethodStreamMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "InvokeMethodStream",
      requestType = org.fractalx.netscope.client.grpc.proto.InvokeRequest.class,
      responseType = org.fractalx.netscope.client.grpc.proto.InvokeResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<org.fractalx.netscope.client.grpc.proto.InvokeRequest,
      org.fractalx.netscope.client.grpc.proto.InvokeResponse> getInvokeMethodStreamMethod() {
    io.grpc.MethodDescriptor<org.fractalx.netscope.client.grpc.proto.InvokeRequest, org.fractalx.netscope.client.grpc.proto.InvokeResponse> getInvokeMethodStreamMethod;
    if ((getInvokeMethodStreamMethod = NetScopeServiceGrpc.getInvokeMethodStreamMethod) == null) {
      synchronized (NetScopeServiceGrpc.class) {
        if ((getInvokeMethodStreamMethod = NetScopeServiceGrpc.getInvokeMethodStreamMethod) == null) {
          NetScopeServiceGrpc.getInvokeMethodStreamMethod = getInvokeMethodStreamMethod =
              io.grpc.MethodDescriptor.<org.fractalx.netscope.client.grpc.proto.InvokeRequest, org.fractalx.netscope.client.grpc.proto.InvokeResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "InvokeMethodStream"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.fractalx.netscope.client.grpc.proto.InvokeRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  org.fractalx.netscope.client.grpc.proto.InvokeResponse.getDefaultInstance()))
              .setSchemaDescriptor(new NetScopeServiceMethodDescriptorSupplier("InvokeMethodStream"))
              .build();
        }
      }
    }
    return getInvokeMethodStreamMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static NetScopeServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<NetScopeServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<NetScopeServiceStub>() {
        @java.lang.Override
        public NetScopeServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new NetScopeServiceStub(channel, callOptions);
        }
      };
    return NetScopeServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static NetScopeServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<NetScopeServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<NetScopeServiceBlockingV2Stub>() {
        @java.lang.Override
        public NetScopeServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new NetScopeServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return NetScopeServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static NetScopeServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<NetScopeServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<NetScopeServiceBlockingStub>() {
        @java.lang.Override
        public NetScopeServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new NetScopeServiceBlockingStub(channel, callOptions);
        }
      };
    return NetScopeServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static NetScopeServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<NetScopeServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<NetScopeServiceFutureStub>() {
        @java.lang.Override
        public NetScopeServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new NetScopeServiceFutureStub(channel, callOptions);
        }
      };
    return NetScopeServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void invokeMethod(org.fractalx.netscope.client.grpc.proto.InvokeRequest request,
        io.grpc.stub.StreamObserver<org.fractalx.netscope.client.grpc.proto.InvokeResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getInvokeMethodMethod(), responseObserver);
    }

    /**
     */
    default void setAttribute(org.fractalx.netscope.client.grpc.proto.SetAttributeRequest request,
        io.grpc.stub.StreamObserver<org.fractalx.netscope.client.grpc.proto.SetAttributeResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSetAttributeMethod(), responseObserver);
    }

    /**
     */
    default void getDocs(org.fractalx.netscope.client.grpc.proto.DocsRequest request,
        io.grpc.stub.StreamObserver<org.fractalx.netscope.client.grpc.proto.DocsResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetDocsMethod(), responseObserver);
    }

    /**
     */
    default io.grpc.stub.StreamObserver<org.fractalx.netscope.client.grpc.proto.InvokeRequest> invokeMethodStream(
        io.grpc.stub.StreamObserver<org.fractalx.netscope.client.grpc.proto.InvokeResponse> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getInvokeMethodStreamMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service NetScopeService.
   */
  public static abstract class NetScopeServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return NetScopeServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service NetScopeService.
   */
  public static final class NetScopeServiceStub
      extends io.grpc.stub.AbstractAsyncStub<NetScopeServiceStub> {
    private NetScopeServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected NetScopeServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new NetScopeServiceStub(channel, callOptions);
    }

    /**
     */
    public void invokeMethod(org.fractalx.netscope.client.grpc.proto.InvokeRequest request,
        io.grpc.stub.StreamObserver<org.fractalx.netscope.client.grpc.proto.InvokeResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getInvokeMethodMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void setAttribute(org.fractalx.netscope.client.grpc.proto.SetAttributeRequest request,
        io.grpc.stub.StreamObserver<org.fractalx.netscope.client.grpc.proto.SetAttributeResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSetAttributeMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void getDocs(org.fractalx.netscope.client.grpc.proto.DocsRequest request,
        io.grpc.stub.StreamObserver<org.fractalx.netscope.client.grpc.proto.DocsResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetDocsMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public io.grpc.stub.StreamObserver<org.fractalx.netscope.client.grpc.proto.InvokeRequest> invokeMethodStream(
        io.grpc.stub.StreamObserver<org.fractalx.netscope.client.grpc.proto.InvokeResponse> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getInvokeMethodStreamMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service NetScopeService.
   */
  public static final class NetScopeServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<NetScopeServiceBlockingV2Stub> {
    private NetScopeServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected NetScopeServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new NetScopeServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    public org.fractalx.netscope.client.grpc.proto.InvokeResponse invokeMethod(org.fractalx.netscope.client.grpc.proto.InvokeRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getInvokeMethodMethod(), getCallOptions(), request);
    }

    /**
     */
    public org.fractalx.netscope.client.grpc.proto.SetAttributeResponse setAttribute(org.fractalx.netscope.client.grpc.proto.SetAttributeRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getSetAttributeMethod(), getCallOptions(), request);
    }

    /**
     */
    public org.fractalx.netscope.client.grpc.proto.DocsResponse getDocs(org.fractalx.netscope.client.grpc.proto.DocsRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getGetDocsMethod(), getCallOptions(), request);
    }

    /**
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<org.fractalx.netscope.client.grpc.proto.InvokeRequest, org.fractalx.netscope.client.grpc.proto.InvokeResponse>
        invokeMethodStream() {
      return io.grpc.stub.ClientCalls.blockingBidiStreamingCall(
          getChannel(), getInvokeMethodStreamMethod(), getCallOptions());
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service NetScopeService.
   */
  public static final class NetScopeServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<NetScopeServiceBlockingStub> {
    private NetScopeServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected NetScopeServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new NetScopeServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public org.fractalx.netscope.client.grpc.proto.InvokeResponse invokeMethod(org.fractalx.netscope.client.grpc.proto.InvokeRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getInvokeMethodMethod(), getCallOptions(), request);
    }

    /**
     */
    public org.fractalx.netscope.client.grpc.proto.SetAttributeResponse setAttribute(org.fractalx.netscope.client.grpc.proto.SetAttributeRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSetAttributeMethod(), getCallOptions(), request);
    }

    /**
     */
    public org.fractalx.netscope.client.grpc.proto.DocsResponse getDocs(org.fractalx.netscope.client.grpc.proto.DocsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetDocsMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service NetScopeService.
   */
  public static final class NetScopeServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<NetScopeServiceFutureStub> {
    private NetScopeServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected NetScopeServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new NetScopeServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<org.fractalx.netscope.client.grpc.proto.InvokeResponse> invokeMethod(
        org.fractalx.netscope.client.grpc.proto.InvokeRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getInvokeMethodMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<org.fractalx.netscope.client.grpc.proto.SetAttributeResponse> setAttribute(
        org.fractalx.netscope.client.grpc.proto.SetAttributeRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSetAttributeMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<org.fractalx.netscope.client.grpc.proto.DocsResponse> getDocs(
        org.fractalx.netscope.client.grpc.proto.DocsRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetDocsMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_INVOKE_METHOD = 0;
  private static final int METHODID_SET_ATTRIBUTE = 1;
  private static final int METHODID_GET_DOCS = 2;
  private static final int METHODID_INVOKE_METHOD_STREAM = 3;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_INVOKE_METHOD:
          serviceImpl.invokeMethod((org.fractalx.netscope.client.grpc.proto.InvokeRequest) request,
              (io.grpc.stub.StreamObserver<org.fractalx.netscope.client.grpc.proto.InvokeResponse>) responseObserver);
          break;
        case METHODID_SET_ATTRIBUTE:
          serviceImpl.setAttribute((org.fractalx.netscope.client.grpc.proto.SetAttributeRequest) request,
              (io.grpc.stub.StreamObserver<org.fractalx.netscope.client.grpc.proto.SetAttributeResponse>) responseObserver);
          break;
        case METHODID_GET_DOCS:
          serviceImpl.getDocs((org.fractalx.netscope.client.grpc.proto.DocsRequest) request,
              (io.grpc.stub.StreamObserver<org.fractalx.netscope.client.grpc.proto.DocsResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_INVOKE_METHOD_STREAM:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.invokeMethodStream(
              (io.grpc.stub.StreamObserver<org.fractalx.netscope.client.grpc.proto.InvokeResponse>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getInvokeMethodMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              org.fractalx.netscope.client.grpc.proto.InvokeRequest,
              org.fractalx.netscope.client.grpc.proto.InvokeResponse>(
                service, METHODID_INVOKE_METHOD)))
        .addMethod(
          getSetAttributeMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              org.fractalx.netscope.client.grpc.proto.SetAttributeRequest,
              org.fractalx.netscope.client.grpc.proto.SetAttributeResponse>(
                service, METHODID_SET_ATTRIBUTE)))
        .addMethod(
          getGetDocsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              org.fractalx.netscope.client.grpc.proto.DocsRequest,
              org.fractalx.netscope.client.grpc.proto.DocsResponse>(
                service, METHODID_GET_DOCS)))
        .addMethod(
          getInvokeMethodStreamMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              org.fractalx.netscope.client.grpc.proto.InvokeRequest,
              org.fractalx.netscope.client.grpc.proto.InvokeResponse>(
                service, METHODID_INVOKE_METHOD_STREAM)))
        .build();
  }

  private static abstract class NetScopeServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    NetScopeServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return org.fractalx.netscope.client.grpc.proto.NetScopeProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("NetScopeService");
    }
  }

  private static final class NetScopeServiceFileDescriptorSupplier
      extends NetScopeServiceBaseDescriptorSupplier {
    NetScopeServiceFileDescriptorSupplier() {}
  }

  private static final class NetScopeServiceMethodDescriptorSupplier
      extends NetScopeServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    NetScopeServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (NetScopeServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new NetScopeServiceFileDescriptorSupplier())
              .addMethod(getInvokeMethodMethod())
              .addMethod(getSetAttributeMethod())
              .addMethod(getGetDocsMethod())
              .addMethod(getInvokeMethodStreamMethod())
              .build();
        }
      }
    }
    return result;
  }
}
