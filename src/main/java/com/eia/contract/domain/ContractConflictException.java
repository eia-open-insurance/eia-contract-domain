package com.eia.contract.domain;

/** A valid command conflicts with the stored aggregate state (mapped to 409). */
public class ContractConflictException extends RuntimeException {

    private final String code;

    public ContractConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
