package com.eia.contract.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Contract projection returned by the domain API. */
public record ContractDto(
        UUID id,
        UUID tenantId,
        String contractNumber,
        String contractType,
        UUID cedentPartyId,
        UUID reinsurerPartyId,
        String status,
        LocalDate effectiveDate,
        LocalDate expirationDate,
        String currency,
        int version,
        Instant createdAt,
        Instant updatedAt
) {}
