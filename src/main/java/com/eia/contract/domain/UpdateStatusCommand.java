package com.eia.contract.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Status transition command carrying the caller's expected optimistic version. */
public record UpdateStatusCommand(
        @NotBlank String status,
        @NotNull @Min(0) Integer expectedVersion
) {}
