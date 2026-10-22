package com.eia.contract.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Public projection of a contract cash call: an interim request for payment on a
 * contract period (SPEC 11.3.4, the Contract {@code ISSUE_CASH_CALL} command).
 *
 * <p>The expert owns call-number generation, the cash-call lifecycle, and the
 * aggregate validation; the domain proxies the projection unchanged. Money is a
 * decimal string (SPEC 10.3). A cash call is created {@code REQUESTED} and moves
 * to {@code ISSUED} then {@code SETTLED}, or to {@code CANCELLED} while it is
 * still a request. {@code settlementReference} is carried only by a settled
 * call.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CashCall(
        @JsonAlias("id") UUID cashCallId,
        String callNumber,
        UUID contractId,
        LocalDate periodStart,
        LocalDate periodEnd,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal amount,
        String currency,
        LocalDate dueDate,
        String status,
        String settlementReference,
        String notes,
        Instant createdAt
) {
}
