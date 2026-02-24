package org.fractalx.netscope.client.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.google.protobuf.ListValue
import com.google.protobuf.NullValue
import com.google.protobuf.Value
import org.fractalx.netscope.client.exception.NetScopeClientException
import spock.lang.Specification

class NetScopeValueConverterSpec extends Specification {

    def objectMapper = new ObjectMapper().registerModule(new JavaTimeModule())
    def converter    = new NetScopeValueConverter(objectMapper)

    // ── Test POJO ─────────────────────────────────────────────────────────────

    static class Point {
        int x
        int y
        Point() {}
        Point(int x, int y) { this.x = x; this.y = y }
    }

    // ── toListValue ───────────────────────────────────────────────────────────

    def "toListValue(null): returns empty ListValue"() {
        when:
        def result = converter.toListValue(null)
        then:
        result.valuesCount == 0
    }

    def "toListValue([]): returns empty ListValue"() {
        when:
        def result = converter.toListValue(new Object[0])
        then:
        result.valuesCount == 0
    }

    def "toListValue([String]): wraps as StringValue"() {
        when:
        def result = converter.toListValue(["hello"] as Object[])
        then:
        result.valuesCount == 1
        result.getValues(0).stringValue == "hello"
    }

    def "toListValue([int]): wraps as NumberValue"() {
        when:
        def result = converter.toListValue([42] as Object[])
        then:
        result.valuesCount == 1
        result.getValues(0).numberValue == 42.0d
    }

    def "toListValue([double]): wraps as NumberValue"() {
        when:
        def result = converter.toListValue([3.14d] as Object[])
        then:
        result.valuesCount == 1
        result.getValues(0).numberValue == 3.14d
    }

    def "toListValue([boolean true]): wraps as BoolValue"() {
        when:
        def result = converter.toListValue([true] as Object[])
        then:
        result.valuesCount == 1
        result.getValues(0).boolValue == true
    }

    def "toListValue([null]): wraps as NullValue"() {
        when:
        def result = converter.toListValue([null] as Object[])
        then:
        result.valuesCount == 1
        result.getValues(0).nullValue == NullValue.NULL_VALUE
    }

    def "toListValue([POJO]): wraps as StructValue with expected fields"() {
        given:
        def point = new Point(3, 7)
        when:
        def result = converter.toListValue([point] as Object[])
        then:
        result.valuesCount == 1
        def struct = result.getValues(0).structValue
        struct.getFieldsOrThrow("x").numberValue == 3.0d
        struct.getFieldsOrThrow("y").numberValue == 7.0d
    }

    def "toListValue: multiple mixed arguments"() {
        when:
        def result = converter.toListValue(["hello", 42, true, null] as Object[])
        then:
        result.valuesCount == 4
        result.getValues(0).stringValue == "hello"
        result.getValues(1).numberValue == 42.0d
        result.getValues(2).boolValue   == true
        result.getValues(3).nullValue   == NullValue.NULL_VALUE
    }

    // ── toValue ───────────────────────────────────────────────────────────────

    def "toValue(null): returns NullValue"() {
        when:
        def result = converter.toValue(null)
        then:
        result.nullValue == NullValue.NULL_VALUE
    }

    def "toValue(String): returns StringValue"() {
        when:
        def result = converter.toValue("world")
        then:
        result.stringValue == "world"
    }

    def "toValue(integer): returns NumberValue"() {
        when:
        def result = converter.toValue(100)
        then:
        result.numberValue == 100.0d
    }

    // ── fromValue(Value, Class) ───────────────────────────────────────────────

    def "fromValue: StringValue to String"() {
        given:
        def v = Value.newBuilder().setStringValue("greet").build()
        expect:
        converter.fromValue(v, String.class) == "greet"
    }

    def "fromValue: NumberValue to Integer"() {
        given:
        def v = Value.newBuilder().setNumberValue(99).build()
        expect:
        converter.fromValue(v, Integer.class) == 99
    }

    def "fromValue: NumberValue to Long"() {
        given:
        def v = Value.newBuilder().setNumberValue(1234567890123L).build()
        expect:
        converter.fromValue(v, Long.class) == 1234567890123L
    }

    def "fromValue: NumberValue to Double"() {
        given:
        def v = Value.newBuilder().setNumberValue(2.718d).build()
        expect:
        (converter.fromValue(v, Double.class) as double) == 2.718d
    }

    def "fromValue: BoolValue to Boolean"() {
        given:
        def v = Value.newBuilder().setBoolValue(true).build()
        expect:
        converter.fromValue(v, Boolean.class) == true
    }

    def "fromValue: NullValue to String returns null"() {
        given:
        def v = Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build()
        expect:
        converter.fromValue(v, String.class) == null
    }

    def "fromValue: void.class always returns null"() {
        given:
        def v = Value.newBuilder().setStringValue("ignored").build()
        expect:
        converter.fromValue(v, void.class) == null
    }

    def "fromValue: Void.class always returns null"() {
        given:
        def v = Value.newBuilder().setNumberValue(42).build()
        expect:
        converter.fromValue(v, Void.class) == null
    }

    def "fromValue: StructValue to POJO"() {
        given:
        def point = new Point(5, 10)
        def listValue = converter.toListValue([point] as Object[])
        def structValue = listValue.getValues(0)
        when:
        def result = converter.fromValue(structValue, Point.class) as Point
        then:
        result.x == 5
        result.y == 10
    }

    // ── fromValue(Value, Type) — generic types ────────────────────────────────

    def "fromValue(Type): ListValue to List<String>"() {
        given:
        def v = Value.newBuilder()
            .setListValue(ListValue.newBuilder()
                .addValues(Value.newBuilder().setStringValue("alpha"))
                .addValues(Value.newBuilder().setStringValue("beta")))
            .build()
        def listOfStringType = new Object() {
            List<String> field
        }.getClass().getDeclaredField("field").genericType
        when:
        def result = converter.fromValue(v, listOfStringType) as List<String>
        then:
        result == ["alpha", "beta"]
    }

    def "fromValue(Type): void generic type returns null"() {
        given:
        def v = Value.newBuilder().setStringValue("x").build()
        expect:
        converter.fromValue(v, void.class as java.lang.reflect.Type) == null
    }

    // ── Round-trip: toListValue → fromValue ───────────────────────────────────

    def "round-trip: String arg → ListValue → String result"() {
        given:
        def listValue = converter.toListValue(["ping"] as Object[])
        def singleValue = listValue.getValues(0)
        when:
        def result = converter.fromValue(singleValue, String.class)
        then:
        result == "ping"
    }

    def "round-trip: POJO arg → ListValue → POJO result"() {
        given:
        def original = new Point(8, 15)
        def listValue = converter.toListValue([original] as Object[])
        def structValue = listValue.getValues(0)
        when:
        def result = converter.fromValue(structValue, Point.class) as Point
        then:
        result.x == 8
        result.y == 15
    }

    // ── Error handling ────────────────────────────────────────────────────────

    def "fromValue: undeserializable content throws NetScopeClientException"() {
        given:
        // A struct value that cannot map to a primitive int
        def v = Value.newBuilder()
            .setStructValue(com.google.protobuf.Struct.newBuilder()
                .putFields("nested", Value.newBuilder().setStringValue("x").build()))
            .build()
        when:
        converter.fromValue(v, int.class)
        then:
        thrown(NetScopeClientException)
    }
}
