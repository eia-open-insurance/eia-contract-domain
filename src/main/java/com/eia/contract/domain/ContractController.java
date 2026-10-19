package com.eia.contract.domain;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Public contract API.
 *
 * <p>The domain service owns business validation and lifecycle decisions; the expert
 * owns persistence. The tenant is always taken from the {@code X-EIA-Tenant} header.</p>
 */
@RestController
@RequestMapping("/api/v1/contracts")
public class ContractController {

    private final ContractClient client;

    public ContractController(ContractClient client) {
        this.client = client;
    }

    @GetMapping
    public List<ContractDto> list(@RequestHeader("X-EIA-Tenant") UUID tenantId) {
        return client.findAll(tenantId);
    }

    @GetMapping("/{id}")
    public ContractDto get(@RequestHeader("X-EIA-Tenant") UUID tenantId, @PathVariable UUID id) {
        return client.findById(tenantId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContractDto create(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                              @Valid @RequestBody CreateContractCommand command) {
        ContractType contractType = parseContractType(command.contractType());
        validateDateRange(command.effectiveDate(), command.expirationDate());
        return client.create(tenantId, command, contractType);
    }

    @PatchMapping("/{id}/status")
    public ContractDto updateStatus(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                    @PathVariable UUID id,
                                    @Valid @RequestBody UpdateStatusCommand command) {
        ContractDto current = client.findById(tenantId, id);
        ContractStatus from = statusOfStored(current.status());
        ContractStatus target = parseStatus(command.status());
        if (!from.canTransitionTo(target)) {
            throw new InvalidStatusTransitionException(from, target);
        }
        return client.updateStatus(tenantId, id, target, command.expectedVersion());
    }

    private static ContractType parseContractType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ContractValidationException("INVALID_CONTRACT_TYPE", "contractType",
                    "Contract type is required");
        }
        try {
            return ContractType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ContractValidationException("INVALID_CONTRACT_TYPE", "contractType",
                    "Unknown contract type: " + raw);
        }
    }

    private static ContractStatus parseStatus(String raw) {
        try {
            return ContractStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ContractValidationException("INVALID_STATUS", "status",
                    "Unknown contract status: " + raw);
        }
    }

    private static ContractStatus statusOfStored(String raw) {
        try {
            return ContractStatus.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ExpertIntegrationException("Contract expert returned an unknown status: " + raw, ex);
        }
    }

    private static void validateDateRange(LocalDate effectiveDate, LocalDate expirationDate) {
        if (effectiveDate != null && expirationDate != null && effectiveDate.isAfter(expirationDate)) {
            throw new ContractValidationException("INVALID_DATE_RANGE", "expirationDate",
                    "effectiveDate must be on or before expirationDate");
        }
    }
}
