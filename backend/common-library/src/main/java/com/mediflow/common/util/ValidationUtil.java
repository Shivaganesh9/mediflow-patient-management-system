package com.mediflow.common.util;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;
import java.util.stream.Collectors;

public final class ValidationUtil {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    private ValidationUtil() {
    }

    public static <T> String validate(T object) {
        Set<ConstraintViolation<T>> violations = VALIDATOR.validate(object);
        if (violations.isEmpty()) {
            return null;
        }
        return violations.stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));
    }
}
