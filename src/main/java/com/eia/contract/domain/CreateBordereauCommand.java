package com.eia.contract.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Records a bordereau received from the cedent (SPEC 11.3.9
 * {@code PROCESS_BORDEREAU}).
 *
 * <p>The tenant is never in the body (it comes from {@code X-EIA-Tenant}), the
 * contract id is never in the body (it is the path), and the book is always
 * ASSUMED. The domain validates the request shape; the expert owns the
 * currency reconciliation, the technical-account linkage, and the lifecycle
 * transitions.</p>
 */
public record CreateBordereauCommand(
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate receivedDate,
        String currency,
        BigDecimal premiumAmount,
        BigDecimal lossAmount,
        BigDecimal commissionAmount,
        BigDecimal otherChargesAmount,
        Integer lineCount,
        String notes
) {
}
