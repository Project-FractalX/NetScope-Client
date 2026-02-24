package org.fractalx.netscope.client.reactive

import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import spock.lang.Specification

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.concurrent.Callable

class ReactiveSupportSpec extends Specification {

    def support = new ReactiveSupport()

    // ── Reactor presence detection ────────────────────────────────────────────

    def "isReactorPresent() returns true when Reactor is on the classpath"() {
        expect:
        support.isReactorPresent()
    }

    // ── isMono ────────────────────────────────────────────────────────────────

    def "isMono(Mono.class) returns true"() {
        expect:
        support.isMono(Mono.class)
    }

    def "isMono(Flux.class) returns false"() {
        expect:
        !support.isMono(Flux.class)
    }

    def "isMono(String.class) returns false"() {
        expect:
        !support.isMono(String.class)
    }

    def "isMono(Object.class) returns false"() {
        expect:
        !support.isMono(Object.class)
    }

    // ── isFlux ────────────────────────────────────────────────────────────────

    def "isFlux(Flux.class) returns true"() {
        expect:
        support.isFlux(Flux.class)
    }

    def "isFlux(Mono.class) returns false"() {
        expect:
        !support.isFlux(Mono.class)
    }

    def "isFlux(String.class) returns false"() {
        expect:
        !support.isFlux(String.class)
    }

    // ── extractTypeArgument ───────────────────────────────────────────────────

    def "extractTypeArgument: Mono<String> returns String.class"() {
        given:
        Type genericType = genericReturnType("monoString")
        expect:
        support.extractTypeArgument(genericType) == String.class
    }

    def "extractTypeArgument: Flux<Integer> returns Integer.class"() {
        given:
        Type genericType = genericReturnType("fluxInteger")
        expect:
        support.extractTypeArgument(genericType) == Integer.class
    }

    def "extractTypeArgument: Mono<List<String>> returns List.class (raw)"() {
        given:
        Type genericType = genericReturnType("monoListString")
        expect:
        support.extractTypeArgument(genericType) == List.class
    }

    def "extractTypeArgument: non-parameterized type returns Object.class"() {
        expect:
        support.extractTypeArgument(String.class) == Object.class
    }

    // ── wrapAsMono ────────────────────────────────────────────────────────────

    def "wrapAsMono: callable result is emitted on subscription"() {
        given:
        def callable = { "hello-from-callable" } as Callable
        when:
        def mono = support.wrapAsMono(callable) as Mono<String>
        then:
        mono.block() == "hello-from-callable"
    }

    def "wrapAsMono: callable error propagates as Mono error"() {
        given:
        def callable = { throw new RuntimeException("boom") } as Callable
        when:
        def mono = support.wrapAsMono(callable) as Mono<String>
        mono.block()
        then:
        thrown(RuntimeException)
    }

    def "wrapAsMono: each subscription re-executes the callable (cold)"() {
        given:
        int count = 0
        def callable = { ++count } as Callable
        def mono = support.wrapAsMono(callable) as Mono<Integer>
        when:
        mono.block()
        mono.block()
        then:
        count == 2
    }

    // ── wrapAsFlux ────────────────────────────────────────────────────────────

    def "wrapAsFlux: sink.next values are emitted in order"() {
        given:
        def consumer = { sink ->
            sink.next("a")
            sink.next("b")
            sink.next("c")
            sink.complete()
        }
        when:
        def flux = support.wrapAsFlux(consumer) as Flux<String>
        def items = flux.collectList().block()
        then:
        items == ["a", "b", "c"]
    }

    def "wrapAsFlux: sink.error propagates as Flux error"() {
        given:
        def consumer = { sink ->
            sink.next("first")
            sink.error(new IllegalStateException("stream-error"))
        }
        when:
        def flux = support.wrapAsFlux(consumer) as Flux<String>
        flux.collectList().block()
        then:
        thrown(IllegalStateException)
    }

    def "wrapAsFlux: empty stream completes without items"() {
        given:
        def consumer = { sink -> sink.complete() }
        when:
        def flux = support.wrapAsFlux(consumer) as Flux<String>
        def items = flux.collectList().block()
        then:
        items.isEmpty()
    }

    // ── Helper: extract generic return type from holder methods ───────────────

    interface TypeHolder {
        Mono<String>      monoString()
        Flux<Integer>     fluxInteger()
        Mono<List<String>> monoListString()
    }

    static Type genericReturnType(String methodName) {
        TypeHolder.getMethod(methodName).genericReturnType
    }
}
