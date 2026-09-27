package org.svenehrke.triptychdemo.cross;

import com.fasterxml.jackson.core.exc.InputCoercionException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.ws.rs.WebApplicationException;
import java.util.Collection;
import java.util.Optional;

/**
 * Turns Jackson <em>deserialization</em> errors - JSON that never reaches a resource method - into
 * messages for the JSON API's {@code 400} body. Not validation: that is {@code parse()}'s job (see validation.md).
 * Used by resource-local {@code @ServerExceptionMapper} methods, so it only affects those resources.
 */
public final class JsonInputErrors {

    private JsonInputErrors() {}

    public static String messageFor(MismatchedInputException e) {
        return describe(pathOf(e)) + ": " + expectation(e.getTargetType());
    }

    /** Empty if the exception is not a wrapped Jackson parser error. */
    public static Optional<String> messageFor(WebApplicationException e) {
        // Quarkus wraps parser-level errors (e.g. "{not json") in a 400 without a body. Inside a record,
        // Jackson first wraps them in a JsonMappingException, which knows the path.
        var wrapper = e.getCause() instanceof JsonMappingException jme && jme.getCause() != null ? jme : null;
        return switch (wrapper != null ? wrapper.getCause() : e.getCause()) {
            case InputCoercionException ignored -> Optional.of(describe(wrapper != null ? pathOf(wrapper) : "") + ": number is out of range");
            case StreamReadException ignored -> Optional.of("request body is not valid JSON");
            case null, default -> Optional.empty();
        };
    }

    private static String pathOf(JsonMappingException e) {
        var path = new StringBuilder();
        for (var ref : e.getPath()) {
            if (ref.getFieldName() != null) path.append(path.isEmpty() ? "" : ".").append(ref.getFieldName());
            if (ref.getIndex() >= 0) path.append("[").append(ref.getIndex()).append("]");
        }
        return path.toString();
    }

    private static String describe(String path) {
        return path.isEmpty() ? "request body" : path;
    }

    private static String expectation(Class<?> targetType) {
        if (targetType == null) return "has an invalid value";
        if (targetType == Integer.class || targetType == int.class) return "must be an integer";
        if (targetType == String.class) return "must be a string";
        if (Collection.class.isAssignableFrom(targetType)) return "must be a list";
        return "has an invalid value";
    }
}
