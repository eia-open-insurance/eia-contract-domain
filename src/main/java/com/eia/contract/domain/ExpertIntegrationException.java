package com.eia.contract.domain;

/** The contract expert could not be reached or returned an unexpected response. */
public class ExpertIntegrationException extends RuntimeException {
    public ExpertIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
