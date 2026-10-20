package com.eia.contract.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Requests an interim cash call for one contract period (SPEC 11.3.9
 * {@code ISSUE_CASH_CALL}).
 *
 * <p>The tenant is never in the body (it comes from {@code X-EIA-Tenant}), the
 * contract id is never in the body (it is the path), and the book is always
 * ASSUMED. The call number and the initial {@code REQUESTED} status are assigned
 * by the expert. The domain validates the request shape; the expert owns the
 * uniqueness, the contract-window, and the lifecycle invariants.</p>
 */
public record CreateCashCallCommand(
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal amount,
        String currency,
        LocalDate dueDate,
        String notes
) {
}
