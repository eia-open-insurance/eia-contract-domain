package com.eia.contract.domain;

import java.util.List;

/** Structured error body returned by the domain API. */
public record ApiError(String code, String message, List<FieldViolation> details) {

    public ApiError(String code, String message) {
        this(code, message, List.of());
    }

    public record FieldViolation(String field, String message) {}

    public static ApiError of(String code, String message, String field, String detail) {
        return new ApiError(code, message, List.of(new FieldViolation(field, detail)));
    }
}
