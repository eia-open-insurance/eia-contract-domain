package com.eia.contract.domain;

/** A command violates a contract invariant. */
public class ContractValidationException extends RuntimeException {
    private final String code;
    private final String field;

    public ContractValidationException(String code, String field, String message) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public String code() {
        return code;
    }

    public String field() {
        return field;
    }
}
