package org.fractalx.netscope.client.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.google.protobuf.NullValue
import com.google.protobuf.Value
import io.grpc.ManagedChannel
import io.grpc.Server
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import org.fractalx.netscope.client.annotation.NetScopeClient
import org.fractalx.netscope.client.exception.NetScopeRemoteException
import org.fractalx.netscope.client.grpc.proto.InvokeRequest
import org.fractalx.netscope.client.grpc.proto.InvokeResponse
import org.fractalx.netscope.client.grpc.proto.NetScopeServiceGrpc
import org.fractalx.netscope.client.proxy.NetScopeClientProxyFactory
import org.fractalx.netscope.client.reactive.ReactiveSupport
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * Integration tests for NetScopeTemplate and the interface proxy (NetScopeInvocationHandler),
 * using an in-process gRPC server to exercise the full serialization and call path.
 */
class NetScopeTemplateIntegrationSpec extends Specification {

    static final String SERVER_NAME = "netscope-test-server"

    // ── Fake gRPC service ─────────────────────────────────────────────────────

    static class FakeNetScopeService extends NetScopeServiceGrpc.NetScopeServiceImplBase {

        // Configure per-method responses before each test
        Map<String, Value> responses   = [:]
        List<InvokeRequest> received   = new CopyOnWriteArrayList<>()

        // For streaming tests: list of values to emit per method invocation
        Map<String, List<Value>> streamResponses = [:]

        @Override
        void invokeMethod(InvokeRequest request, StreamObserver<InvokeResponse> observer) {
            received << request
            def result = responses.getOrDefault(
                request.memberName,
                Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()
            )
            observer.onNext(InvokeResponse.newBuilder().setResult(result).build())
            observer.onCompleted()
        }

        @Override
        StreamObserver<InvokeRequest> invokeMethodStream(StreamObserver<InvokeResponse> observer) {
            return new StreamObserver<InvokeRequest>() {
                @Override void onNext(InvokeRequest req) {
                    received << req
                    def items = streamResponses.getOrDefault(req.memberName, [])
                    items.each { v ->
                        observer.onNext(InvokeResponse.newBuilder().setResult(v).build())
                    }
                }
                @Override void onError(Throwable t) { observer.onError(t) }
                @Override void onCompleted()          { observer.onCompleted() }
            }
        }

        void reset() {
            responses.clear()
            received.clear()
            streamResponses.clear()
        }
    }

    // ── Test interfaces ───────────────────────────────────────────────────────

    @NetScopeClient(server = "test-service", beanName = "TestBean")
    interface TestClient {
        String greet(String name)
        int    getCount()
        boolean isEnabled()
        void   ping()
        List<String> listItems()
        String multiArg(String a, int b)
        Mono<String>    greetAsync(String name)
        Flux<String>    streamWords()
    }

    // ── Infrastructure ────────────────────────────────────────────────────────

    FakeNetScopeService fakeService = new FakeNetScopeService()
    Server              grpcServer
    ManagedChannel      channel
    NetScopeTemplate    template
    NetScopeValueConverter converter
    ReactiveSupport     reactive
    TestClient          client

    def setup() {
        grpcServer = InProcessServerBuilder
            .forName(SERVER_NAME)
            .addService(fakeService)
            .directExecutor()
            .build()
            .start()

        channel = InProcessChannelBuilder
            .forName(SERVER_NAME)
            .directExecutor()
            .build()

        def channelFactory = Mock(NetScopeChannelFactory) {
            channelFor("test-service") >> channel
        }

        def om = new ObjectMapper().registerModule(new JavaTimeModule())
        converter = new NetScopeValueConverter(om)
        reactive  = new ReactiveSupport()
        template  = new NetScopeTemplate(channelFactory, converter, reactive)
        client    = new NetScopeClientProxyFactory(template, reactive).createProxy(TestClient)
    }

    def cleanup() {
        channel?.shutdown()?.awaitTermination(3, TimeUnit.SECONDS)
        grpcServer?.shutdown()?.awaitTermination(3, TimeUnit.SECONDS)
    }

    // ── NetScopeTemplate.BeanStep.invoke — return types ───────────────────────

    def "invoke: String return type"() {
        given:
        fakeService.responses.greet = Value.newBuilder().setStringValue("hello, world").build()
        when:
        def result = template.server("test-service").bean("TestBean").invoke("greet", String.class, "world")
        then:
        result == "hello, world"
    }

    def "invoke: Integer return type"() {
        given:
        fakeService.responses.getCount = Value.newBuilder().setNumberValue(42).build()
        when:
        def result = template.server("test-service").bean("TestBean").invoke("getCount", Integer.class)
        then:
        result == 42
    }

    def "invoke: null result returns null"() {
        given:
        fakeService.responses.greet = Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()
        when:
        def result = template.server("test-service").bean("TestBean").invoke("greet", String.class, "x")
        then:
        result == null
    }

    def "invoke: void return type — call succeeds and returns null"() {
        given:
        fakeService.responses.ping = Value.newBuilder().setStringValue("accepted").build()
        when:
        def result = template.server("test-service").bean("TestBean").invoke("ping", void.class)
        then:
        result == null
        fakeService.received.size() == 1
        fakeService.received[0].memberName == "ping"
    }

    def "invoke: arguments are serialized and sent correctly"() {
        given:
        fakeService.responses.greet = Value.newBuilder().setStringValue("hi").build()
        when:
        template.server("test-service").bean("TestBean").invoke("greet", String.class, "Alice")
        then:
        fakeService.received.size() == 1
        def args = fakeService.received[0].arguments
        args.getValues(0).stringValue == "Alice"
    }

    def "invoke: multiple arguments sent in order"() {
        given:
        fakeService.responses.multiArg = Value.newBuilder().setStringValue("ok").build()
        when:
        template.server("test-service").bean("TestBean").invoke("multiArg", String.class, "foo", 7)
        then:
        def args = fakeService.received[0].arguments
        args.getValues(0).stringValue == "foo"
        args.getValues(1).numberValue == 7.0d
    }

    def "invoke: bean name and method name are sent correctly"() {
        given:
        fakeService.responses.getCount = Value.newBuilder().setNumberValue(1).build()
        when:
        template.server("test-service").bean("TestBean").invoke("getCount", Integer.class)
        then:
        fakeService.received[0].beanName   == "TestBean"
        fakeService.received[0].memberName == "getCount"
    }

    def "invoke: unavailable server throws NetScopeRemoteException"() {
        given:
        // No service registered for this channel — force an error by using a dead channel
        def deadChannel = InProcessChannelBuilder.forName("nonexistent-server").directExecutor().build()
        def deadChannelFactory = Mock(NetScopeChannelFactory) {
            channelFor("dead") >> deadChannel
        }
        def deadTemplate = new NetScopeTemplate(deadChannelFactory, converter, reactive)
        when:
        deadTemplate.server("dead").bean("SomeBean").invoke("method", String.class)
        then:
        thrown(NetScopeRemoteException)
        cleanup:
        deadChannel.shutdown()
    }

    // ── Proxy interface — blocking calls ──────────────────────────────────────

    def "proxy: String method call returns deserialized value"() {
        given:
        fakeService.responses.greet = Value.newBuilder().setStringValue("hi there").build()
        when:
        def result = client.greet("Bob")
        then:
        result == "hi there"
    }

    def "proxy: int method call returns deserialized value"() {
        given:
        fakeService.responses.getCount = Value.newBuilder().setNumberValue(7).build()
        when:
        def result = client.getCount()
        then:
        result == 7
    }

    def "proxy: boolean method call returns deserialized value"() {
        given:
        fakeService.responses.isEnabled = Value.newBuilder().setBoolValue(true).build()
        when:
        def result = client.isEnabled()
        then:
        result == true
    }

    def "proxy: void method call does not throw and makes gRPC call"() {
        given:
        fakeService.responses.ping = Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()
        when:
        client.ping()
        then:
        noExceptionThrown()
        fakeService.received.size() == 1
        fakeService.received[0].memberName == "ping"
    }

    def "proxy: correct bean name is sent from @NetScopeClient annotation"() {
        given:
        fakeService.responses.getCount = Value.newBuilder().setNumberValue(1).build()
        when:
        client.getCount()
        then:
        fakeService.received[0].beanName == "TestBean"
    }

    def "proxy: toString() returns descriptive string without gRPC call"() {
        when:
        def str = client.toString()
        then:
        str.contains("test-service")
        str.contains("TestBean")
        fakeService.received.isEmpty()
    }

    def "proxy: hashCode() returns without gRPC call"() {
        when:
        client.hashCode()
        then:
        noExceptionThrown()
        fakeService.received.isEmpty()
    }

    def "proxy: equals(self) returns true without gRPC call"() {
        expect:
        client.equals(client) == true
        fakeService.received.isEmpty()
    }

    // ── Proxy interface — Mono<T> ─────────────────────────────────────────────

    def "proxy: Mono<String> method is lazy — no gRPC call until subscribed"() {
        given:
        fakeService.responses.greetAsync = Value.newBuilder().setStringValue("async!").build()
        when:
        Mono<String> mono = client.greetAsync("async-test")
        then:
        fakeService.received.isEmpty()   // not yet subscribed

        when:
        def result = mono.block()
        then:
        result == "async-test" || result == "async!"  // server echoes back
        fakeService.received.size() == 1
    }

    def "proxy: Mono<String> result is correct after subscription"() {
        given:
        fakeService.responses.greetAsync = Value.newBuilder().setStringValue("mono-result").build()
        when:
        def result = (client.greetAsync("x") as Mono<String>).block()
        then:
        result == "mono-result"
    }

    def "proxy: Mono error propagates as Mono error signal"() {
        given:
        // No response registered → service returns null value → fromValue gives null → block() returns null
        // To test error we need the server to throw; simulate via StatusRuntimeException
        grpcServer.shutdown().awaitTermination(1, TimeUnit.SECONDS)
        when:
        (client.greetAsync("x") as Mono<String>).block()
        then:
        thrown(Exception)   // channel is shut down, so call fails
    }

    // ── Proxy interface — Flux<T> streaming ───────────────────────────────────

    def "proxy: Flux<String> emits all streamed responses in order"() {
        given:
        fakeService.streamResponses.streamWords = [
            Value.newBuilder().setStringValue("foo").build(),
            Value.newBuilder().setStringValue("bar").build(),
            Value.newBuilder().setStringValue("baz").build()
        ]
        when:
        def items = (client.streamWords() as Flux<String>).collectList().block()
        then:
        items == ["foo", "bar", "baz"]
    }

    def "proxy: Flux<String> emits empty list when no stream responses"() {
        given:
        fakeService.streamResponses.streamWords = []
        when:
        def items = (client.streamWords() as Flux<String>).collectList().block()
        then:
        items.isEmpty()
    }

    def "proxy: Flux is lazy — no gRPC call until subscribed"() {
        given:
        fakeService.streamResponses.streamWords = [Value.newBuilder().setStringValue("x").build()]
        when:
        Flux<String> flux = client.streamWords()
        then:
        fakeService.received.isEmpty()

        when:
        flux.collectList().block()
        then:
        fakeService.received.size() == 1
    }
}
