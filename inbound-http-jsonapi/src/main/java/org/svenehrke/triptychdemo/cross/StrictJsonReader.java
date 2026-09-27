package org.svenehrke.triptychdemo.cross;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import java.lang.reflect.Type;
import java.util.function.BiFunction;

/**
 * For {@code @CustomDeserialization}: stricter than the global {@code ObjectMapper} (which the Kafka
 * deserializers share, so it stays untouched): no silent scalar coercions such as {@code "5"} or
 * {@code 5.7} to an int, or {@code 123} to a String. A rejected coercion surfaces as a
 * {@code MismatchedInputException}, see {@link JsonInputErrors}.
 */
public class StrictJsonReader implements BiFunction<ObjectMapper, Type, ObjectReader> {

    @Override
    public ObjectReader apply(ObjectMapper globalMapper, Type type) {
        var strict = globalMapper.copy();
        strict.coercionConfigFor(LogicalType.Integer)
            .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        strict.coercionConfigFor(LogicalType.Textual)
            .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
            .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        return strict.reader();
    }
}
