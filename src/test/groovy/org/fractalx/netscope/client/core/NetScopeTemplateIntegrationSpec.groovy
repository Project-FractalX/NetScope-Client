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
import org.fractalx.netscope.client.annotation.SetAttribute
import org.fractalx.netscope.client.exception.NetScopeRemoteException
import org.fractalx.netscope.client.grpc.proto.DocsRequest
import org.fractalx.netscope.client.grpc.proto.DocsResponse
import org.fractalx.netscope.client.grpc.proto.InvokeRequest
import org.fractalx.netscope.client.grpc.proto.InvokeResponse
import org.fractalx.netscope.client.grpc.proto.MethodInfo
import org.fractalx.netscope.client.grpc.proto.NetScopeServiceGrpc
import org.fractalx.netscope.client.grpc.proto.SetAttributeRequest
import org.fractalx.netscope.client.grpc.proto.SetAttributeResponse
import org.fractalx.netscope.client.proxy.NetScopeClientProxyFactory
import org.fractalx.netscope.client.reactive.ReactiveSupport
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Integration tests for NetScopeTemplate and the interface proxy (NetScopeInvocationHandler),
 * using an in-process gRPC server to exercise the full serialization and call path.
 */
class NetScopeTemplateIntegrationSpec extends Specification {

    static final String SERVER_NAME = "netscope-test-server"

    // ── Fake gRPC service ─────────────────────────────────────────────────────

    static class FakeNetScopeService extends NetScopeServiceGrpc.NetScopeServiceImplBase {

        // InvokeMethod
        Map<String, Value>       responses          = [:]
        List<InvokeRequest>      received           = new CopyOnWriteArrayList<>()

        // InvokeMethodStream
        Map<String, List<Value>> streamResponses    = [:]

        // SetAttribute
        List<SetAttributeRequest> setAttrReceived   = new CopyOnWriteArrayList<>()
        Map<String, Value>        setAttrPrevValues  = [:]   // previous value per attribute name

        // GetDocs
        DocsResponse docsResponseToReturn = DocsResponse.newBuilder().build()

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
        void setAttribute(SetAttributeRequest request, StreamObserver<SetAttributeResponse> observer) {
            setAttrReceived << request
            def prev = setAttrPrevValues.getOrDefault(
                request.attributeName,
                Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()
            )
            observer.onNext(SetAttributeResponse.newBuilder().setPreviousValue(prev).build())
            observer.onCompleted()
        }

        @Override
        void getDocs(DocsRequest request, StreamObserver<DocsResponse> observer) {
            observer.onNext(docsResponseToReturn)
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
            setAttrReceived.clear()
            setAttrPrevValues.clear()
        }
    }

    // ── Test interfaces ───────────────────────────────────────────────────────

    @NetScopeClient(server = "test-service", beanName = "TestBean")
    interface TestClient {
        String       greet(String name)
        int          getCount()
        boolean      isEnabled()
        void         ping()
        List<String> listItems()
        String       multiArg(String a, int b)
        Mono<String> greetAsync(String name)
        Flux<String> streamWords()

        // P1a: field write via SetAttribute RPC
        @SetAttribute("counter")
        int setCounter(int value)

        // P1a: field name defaults to method name when @SetAttribute has no value
        @SetAttribute
        String description(String newValue)
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

    // ── P0: parameter_types auto-population ───────────────────────────────────

    def "proxy: single-arg method sends its parameter type"() {
        given:
        fakeService.responses.greet = Value.newBuilder().setStringValue("hi").build()
        when:
        client.greet("Alice")
        then:
        fakeService.received[0].parameterTypesList == ["String"]
    }

    def "proxy: multi-arg method sends all parameter types in order"() {
        given:
        fakeService.responses.multiArg = Value.newBuilder().setStringValue("ok").build()
        when:
        client.multiArg("foo", 5)
        then:
        fakeService.received[0].parameterTypesList == ["String", "int"]
    }

    def "proxy: no-arg method sends empty parameter types list"() {
        given:
        fakeService.responses.getCount = Value.newBuilder().setNumberValue(1).build()
        when:
        client.getCount()
        then:
        fakeService.received[0].parameterTypesList.isEmpty()
    }

    def "template: withParameterTypes sends the specified type names"() {
        given:
        fakeService.responses.greet = Value.newBuilder().setStringValue("hi").build()
        when:
        template.server("test-service").bean("TestBean")
            .withParameterTypes("String")
            .invoke("greet", String.class, "Alice")
        then:
        fakeService.received[0].parameterTypesList == ["String"]
    }

    def "template: withParameterTypes overrides default empty list"() {
        given:
        fakeService.responses.process = Value.newBuilder().setStringValue("done").build()
        when:
        template.server("test-service").bean("TestBean")
            .withParameterTypes("Object")
            .invoke("process", String.class, "v")
        then:
        fakeService.received[0].parameterTypesList == ["Object"]
    }

    def "template: without withParameterTypes no parameter_types are sent"() {
        given:
        fakeService.responses.greet = Value.newBuilder().setStringValue("hi").build()
        when:
        template.server("test-service").bean("TestBean").invoke("greet", String.class, "Alice")
        then:
        fakeService.received[0].parameterTypesList.isEmpty()
    }

    def "template: withParameterTypes returns a new BeanStep without mutating the original"() {
        given:
        fakeService.responses.greet = Value.newBuilder().setStringValue("hi").build()
        def beanStep = template.server("test-service").bean("TestBean")
        def typedStep = beanStep.withParameterTypes("String")
        when:
        beanStep.invoke("greet", String.class, "Alice")
        typedStep.invoke("greet", String.class, "Bob")
        then:
        fakeService.received[0].parameterTypesList.isEmpty()  // original untouched
        fakeService.received[1].parameterTypesList == ["String"]
    }

    // ── P1a: SetAttribute — template ──────────────────────────────────────────

    def "setAttribute: sends SetAttributeRequest with correct beanName and attributeName"() {
        given:
        fakeService.setAttrPrevValues.counter = Value.newBuilder().setNumberValue(0).build()
        when:
        template.server("test-service").bean("TestBean").setAttribute("counter", 42)
        then:
        fakeService.setAttrReceived.size() == 1
        fakeService.setAttrReceived[0].beanName      == "TestBean"
        fakeService.setAttrReceived[0].attributeName == "counter"
    }

    def "setAttribute: sends the new value in the request"() {
        given:
        fakeService.setAttrPrevValues.counter = Value.newBuilder().setNumberValue(0).build()
        when:
        template.server("test-service").bean("TestBean").setAttribute("counter", 99)
        then:
        fakeService.setAttrReceived[0].value.numberValue == 99.0d
    }

    def "setAttribute: returns the previous field value (untyped)"() {
        given:
        fakeService.setAttrPrevValues.counter = Value.newBuilder().setNumberValue(5).build()
        when:
        def prev = template.server("test-service").bean("TestBean").setAttribute("counter", 10)
        then:
        (prev as Double) == 5.0d
    }

    def "setAttribute: returns the previous field value typed to returnType"() {
        given:
        fakeService.setAttrPrevValues.counter = Value.newBuilder().setNumberValue(5).build()
        when:
        def prev = template.server("test-service").bean("TestBean").setAttribute("counter", 10, Integer.class)
        then:
        prev == 5
    }

    def "setAttribute: does NOT call InvokeMethod"() {
        given:
        fakeService.setAttrPrevValues.counter = Value.newBuilder().setNumberValue(0).build()
        when:
        template.server("test-service").bean("TestBean").setAttribute("counter", 1)
        then:
        fakeService.received.isEmpty()
        fakeService.setAttrReceived.size() == 1
    }

    // ── P1a: SetAttribute — proxy @SetAttribute annotation ────────────────────

    def "proxy @SetAttribute: dispatches to SetAttribute RPC with annotated field name"() {
        given:
        fakeService.setAttrPrevValues.counter = Value.newBuilder().setNumberValue(0).build()
        when:
        client.setCounter(99)
        then:
        fakeService.setAttrReceived.size() == 1
        fakeService.setAttrReceived[0].attributeName == "counter"
        fakeService.setAttrReceived[0].value.numberValue == 99.0d
    }

    def "proxy @SetAttribute: returns the previous value"() {
        given:
        fakeService.setAttrPrevValues.counter = Value.newBuilder().setNumberValue(7).build()
        when:
        def prev = client.setCounter(100)
        then:
        prev == 7
    }

    def "proxy @SetAttribute: does NOT call InvokeMethod"() {
        given:
        fakeService.setAttrPrevValues.counter = Value.newBuilder().setNumberValue(0).build()
        when:
        client.setCounter(1)
        then:
        fakeService.received.isEmpty()
    }

    def "proxy @SetAttribute: defaults field name to method name when value is blank"() {
        given:
        fakeService.setAttrPrevValues.description = Value.newBuilder().setStringValue("old").build()
        when:
        client.description("new desc")
        then:
        fakeService.setAttrReceived[0].attributeName == "description"
    }

    // ── P1b: GetDocs ──────────────────────────────────────────────────────────

    def "getDocs: returns a non-null DocsResponse"() {
        when:
        def docs = template.server("test-service").getDocs()
        then:
        docs != null
        docs instanceof DocsResponse
    }

    def "getDocs: returns the response from the server (empty by default)"() {
        when:
        def docs = template.server("test-service").getDocs()
        then:
        docs.methodsList.isEmpty()
    }

    def "getDocs: returns MethodInfo entries set by the server"() {
        given:
        def mi = MethodInfo.newBuilder()
            .setBeanName("TestBean")
            .setMemberName("greet")
            .build()
        fakeService.docsResponseToReturn = DocsResponse.newBuilder().addMethods(mi).build()
        when:
        def docs = template.server("test-service").getDocs()
        then:
        docs.methodsList.size() == 1
        docs.methodsList[0].beanName   == "TestBean"
        docs.methodsList[0].memberName == "greet"
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
        grpcServer.shutdown().awaitTermination(1, TimeUnit.SECONDS)
        when:
        (client.greetAsync("x") as Mono<String>).block()
        then:
        thrown(Exception)
    }

    // ── Proxy interface — Flux<T> streaming (invokeFlux) ─────────────────────

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

    // ── P3: invokeBatchStream ─────────────────────────────────────────────────

    def "invokeBatchStream: sends multiple requests over one gRPC stream"() {
        given:
        fakeService.streamResponses["greet"] = [Value.newBuilder().setStringValue("pong").build()]
        when:
        def results = (template.server("test-service").bean("TestBean")
            .invokeBatchStream(String.class,
                MethodCall.of("greet", "a"),
                MethodCall.of("greet", "b")) as Flux<String>)
            .collectList().block()
        then:
        results == ["pong", "pong"]
        fakeService.received.size() == 2
        fakeService.received[0].memberName == "greet"
        fakeService.received[1].memberName == "greet"
    }

    def "invokeBatchStream: different method names within one stream"() {
        given:
        fakeService.streamResponses["greet"]    = [Value.newBuilder().setStringValue("hello").build()]
        fakeService.streamResponses["listItems"] = [Value.newBuilder().setStringValue("item1").build()]
        when:
        def results = (template.server("test-service").bean("TestBean")
            .invokeBatchStream(String.class,
                MethodCall.of("greet", "x"),
                MethodCall.of("listItems")) as Flux<String>)
            .collectList().block()
        then:
        results == ["hello", "item1"]
    }

    def "invokeBatchStream: MethodCall.onBean overrides default bean name"() {
        given:
        fakeService.streamResponses["method"] = [Value.newBuilder().setStringValue("x").build()]
        when:
        (template.server("test-service").bean("TestBean")
            .invokeBatchStream(String.class,
                MethodCall.onBean("OtherBean", "method")) as Flux<String>)
            .collectList().block()
        then:
        fakeService.received[0].beanName == "OtherBean"
    }

    def "invokeBatchStream: default bean name used when MethodCall has no bean override"() {
        given:
        fakeService.streamResponses["greet"] = [Value.newBuilder().setStringValue("hi").build()]
        when:
        (template.server("test-service").bean("TestBean")
            .invokeBatchStream(String.class, MethodCall.of("greet")) as Flux<String>)
            .collectList().block()
        then:
        fakeService.received[0].beanName == "TestBean"
    }

    def "invokeBatchStream: empty call list completes with empty Flux"() {
        when:
        def results = (template.server("test-service").bean("TestBean")
            .invokeBatchStream(String.class) as Flux<String>)
            .collectList().block()
        then:
        results.isEmpty()
        fakeService.received.isEmpty()
    }

    // ── P3: BidiStreamSession ─────────────────────────────────────────────────

    def "BidiStreamSession: sends requests and receives all responses"() {
        given:
        fakeService.streamResponses["greet"] = [Value.newBuilder().setStringValue("pong").build()]
        def session = template.server("test-service").openBidiStream("TestBean", String.class)
        def results = new CopyOnWriteArrayList<String>()
        def latch = new CountDownLatch(2)

        (session.responseFlux() as Flux<String>).subscribe(
            { v -> results.add(v); latch.countDown() }, {}, {}
        )

        when:
        session.send("greet", "1")
        session.send("greet", "2")
        session.complete()

        then:
        latch.await(3, TimeUnit.SECONDS)
        results.size() == 2
    }

    def "BidiStreamSession: send returns the session itself for fluent chaining"() {
        given:
        fakeService.streamResponses["greet"] = [Value.newBuilder().setStringValue("ok").build()]
        def session = template.server("test-service").openBidiStream("TestBean", String.class)
        (session.responseFlux() as Flux<String>).subscribe({}, {}, {})

        when:
        def returned = session.send("greet")

        then:
        returned.is(session)

        cleanup:
        session.complete()
    }

    def "BidiStreamSession: sendTo with explicit beanName overrides the session default"() {
        given:
        fakeService.streamResponses["method"] = [Value.newBuilder().setStringValue("x").build()]
        def session = template.server("test-service").openBidiStream("TestBean", String.class)
        def latch = new CountDownLatch(1)
        (session.responseFlux() as Flux<String>).subscribe({ latch.countDown() }, {}, {})

        when:
        session.sendTo("OtherBean", "method")
        session.complete()

        then:
        latch.await(3, TimeUnit.SECONDS)
        fakeService.received[0].beanName == "OtherBean"
    }

    def "BidiStreamSession: complete signals end of stream to the server"() {
        given:
        def session = template.server("test-service").openBidiStream("TestBean", String.class)
        def completed = new CountDownLatch(1)
        (session.responseFlux() as Flux<String>).subscribe({}, {}, { completed.countDown() })

        when:
        session.complete()

        then:
        completed.await(3, TimeUnit.SECONDS)
    }

    def "BidiStreamSession: openBidiStream with parameterTypes passes them on every request"() {
        given:
        fakeService.streamResponses["greet"] = [Value.newBuilder().setStringValue("ok").build()]
        def session = template.server("test-service").openBidiStream("TestBean", String.class, "String")
        def latch = new CountDownLatch(1)
        (session.responseFlux() as Flux<String>).subscribe({ latch.countDown() }, {}, {})

        when:
        session.send("greet", "Alice")
        session.complete()

        then:
        latch.await(3, TimeUnit.SECONDS)
        fakeService.received[0].parameterTypesList == ["String"]
    }
}
