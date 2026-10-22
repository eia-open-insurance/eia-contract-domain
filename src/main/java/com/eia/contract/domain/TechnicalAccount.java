package com.eia.contract.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public projection of a contract technical account (SPEC 11.3.4
 * {@code technical_account}).
 *
 * <p>A technical account is the periodic financial statement of the treaty. The
 * expert owns production and the balance calculation; the domain proxies the
 * projection unchanged. Money is a decimal string (SPEC 10.3), and a positive
 * {@code balance} is due to the reinsurer. {@code settlementReference} is
 * carried only by a settled account.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TechnicalAccount(
        @JsonAlias("id") UUID accountId,
        String accountNumber,
        UUID contractId,
        LocalDate periodStart,
        LocalDate periodEnd,
        String currency,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cededPremium,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cededLosses,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal commission,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal otherCharges,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal balance,
        String status,
        String settlementReference,
        Instant createdAt
) {
}
