package com.eia.contract.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Create-draft command. The tenant comes from {@code X-EIA-Tenant}, never from the body.
 * A freshly created contract is always in {@code DRAFT}; later states are transitions.
 */
public record CreateContractCommand(
        @NotBlank
        @Size(max = 32)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._/-]{1,31}",
                message = "must start with an alphanumeric character and contain only letters, digits, '.', '_', '/' or '-'")
        String contractNumber,
        @NotBlank String contractType,
        @NotNull UUID cedentPartyId,
        @NotNull UUID reinsurerPartyId,
        @NotNull LocalDate effectiveDate,
        @NotNull LocalDate expirationDate,
        @NotBlank
        @Pattern(regexp = "[A-Z]{3}", message = "must be a three-letter uppercase ISO currency code")
        String currency
) {}
