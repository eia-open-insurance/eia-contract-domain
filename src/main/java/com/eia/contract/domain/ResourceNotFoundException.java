package com.eia.contract.domain;

/** A contract or one of its child resources does not exist (mapped to 404). */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
