package com.eia.contract.domain;

/**
 * Raised when a caller presents a book other than ASSUMED. The contract
 * vertical hides the record as a 404 so the book wall does not leak the
 * existence of assumed-book data to a direct-book caller.
 */
public class ContractBookScopeException extends RuntimeException {

    public ContractBookScopeException() {
        super("Contract is an ASSUMED-only vertical; X-EIA-Book must be ASSUMED");
    }
}
