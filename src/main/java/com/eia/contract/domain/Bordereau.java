package com.eia.contract.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public projection of a contract bordereau: a schedule of premium, claims,
 * commissions, and charges submitted by the cedent (SPEC 11.3.4
 * {@code contract_bordereau}).
 *
 * <p>The expert owns the bordereau lifecycle and its financial reconciliation;
 * the domain proxies the projection unchanged. Money is a decimal string
 * (SPEC 10.3), and a bordereau may be linked to the technical account it feeds
 * through {@code technicalAccountId}.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Bordereau(
        @JsonAlias("id") UUID bordereauId,
        String bordereauNumber,
        UUID contractId,
        LocalDate periodStart,
        LocalDate periodEnd,
        LocalDate receivedDate,
        String currency,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal premiumAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal lossAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal commissionAmount,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal otherChargesAmount,
        int lineCount,
        String notes,
        String status,
        UUID technicalAccountId,
        Instant createdAt
) {
}
