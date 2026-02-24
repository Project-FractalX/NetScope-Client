package org.fractalx.netscope.client.core;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ListValue;
import com.google.protobuf.NullValue;
import com.google.protobuf.Value;
import com.google.protobuf.util.JsonFormat;
import org.fractalx.netscope.client.exception.NetScopeClientException;

import java.lang.reflect.Type;

/**
 * Bridges Java objects and protobuf {@link Value}/{@link ListValue} types.
 *
 * <p>Serialization path (Java → protobuf):</p>
 * <ol>
 *   <li>Jackson serializes each argument to JSON</li>
 *   <li>{@link JsonFormat#parser()} parses the JSON into a protobuf {@link Value}</li>
 *   <li>All argument values are wrapped into a {@link ListValue}</li>
 * </ol>
 *
 * <p>Deserialization path (protobuf → Java):</p>
 * <ol>
 *   <li>{@link JsonFormat#printer()} emits the {@link Value} as JSON</li>
 *   <li>Jackson deserializes the JSON into the target Java type</li>
 * </ol>
 */
public class NetScopeValueConverter {

    private final ObjectMapper objectMapper;
    private final JsonFormat.Printer printer;
    private final JsonFormat.Parser parser;

    public NetScopeValueConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.printer = JsonFormat.printer().preservingProtoFieldNames().omittingInsignificantWhitespace();
        this.parser  = JsonFormat.parser().ignoringUnknownFields();
    }

    /**
     * Converts an array of Java arguments to a protobuf {@link ListValue}.
     * A {@code null} argument array is treated as empty.
     */
    public ListValue toListValue(Object[] args) {
        ListValue.Builder builder = ListValue.newBuilder();
        if (args != null) {
            for (Object arg : args) {
                builder.addValues(toValue(arg));
            }
        }
        return builder.build();
    }

    /**
     * Converts a single Java object to a protobuf {@link Value}.
     */
    public Value toValue(Object obj) {
        if (obj == null) {
            return Value.newBuilder().setNullValue(NullValue.NULL_VALUE).build();
        }
        try {
            String json = objectMapper.writeValueAsString(obj);
            Value.Builder vb = Value.newBuilder();
            parser.merge(json, vb);
            return vb.build();
        } catch (Exception e) {
            throw new NetScopeClientException(
                "Failed to convert argument to protobuf Value: " + obj.getClass().getSimpleName(), e);
        }
    }

    /**
     * Converts a protobuf {@link Value} to the specified Java {@link Class}.
     *
     * @return {@code null} if {@code targetType} is {@code void}/{@code Void}
     */
    public <T> T fromValue(Value result, Class<T> targetType) {
        if (targetType == void.class || targetType == Void.class) {
            return null;
        }
        try {
            String json = printer.print(result);
            return objectMapper.readValue(json, targetType);
        } catch (Exception e) {
            throw new NetScopeClientException(
                "Failed to convert protobuf Value to " + targetType.getSimpleName(), e);
        }
    }

    /**
     * Converts a protobuf {@link Value} to the specified generic Java {@link Type}.
     * Use this overload for parameterized types like {@code List<Foo>}.
     *
     * @return {@code null} if the result is empty or target is void
     */
    public Object fromValue(Value result, Type genericType) {
        if (genericType == void.class || genericType == Void.class) {
            return null;
        }
        try {
            String json = printer.print(result);
            JavaType javaType = objectMapper.constructType(genericType);
            return objectMapper.readValue(json, javaType);
        } catch (Exception e) {
            throw new NetScopeClientException(
                "Failed to convert protobuf Value to " + genericType.getTypeName(), e);
        }
    }
}
