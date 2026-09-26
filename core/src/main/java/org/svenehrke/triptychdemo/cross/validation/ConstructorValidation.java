package org.svenehrke.triptychdemo.cross.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import java.lang.reflect.Constructor;
import java.util.Set;

/**
 * Shared plumbing for the validated domain records (see docs/architecture/validation.md): each record
 * validates candidate arguments against the parameter constraints of one of its own constructors,
 * without invoking it.
 */
public final class ConstructorValidation {

	/** One validator for all records: building a {@code ValidatorFactory} is expensive, and a {@code Validator} is thread-safe. */
	private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

	private ConstructorValidation() {}

	/** Looks up a declared constructor by its parameter types; a missing one is a programming error. */
	public static <T> Constructor<T> declaredConstructor(Class<T> type, Class<?>... parameterTypes) {
		try {
			return type.getDeclaredConstructor(parameterTypes);
		} catch (NoSuchMethodException e) {
			throw new IllegalStateException(e);
		}
	}

	/** Validates {@code args} against {@code constructor}'s parameter constraints, without invoking it. */
	public static <T> Set<ConstraintViolation<T>> validateParameters(Constructor<T> constructor, Object... args) {
		return VALIDATOR.forExecutables().validateConstructorParameters(constructor, args);
	}
}
