package com.eia.contract.domain;

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

import java.util.List;
import java.util.UUID;

/**
 * Public bordereau API for an ASSUMED contract (SPEC 11.3.4,
 * SPEC 11.3.10).
 *
 * <p>The tenant comes from {@code X-EIA-Tenant}. The Contract vertical is
 * ASSUMED-only, so a supplied {@code X-EIA-Book} must be ASSUMED; an absent
 * header is accepted for compatibility with the other contract proxy routes.
 * Every call forwards the tenant and the ASSUMED book to the expert, and the
 * expert's structured {@code 400}/{@code 404}/{@code 409} problems are
 * preserved.</p>
 */
@RestController
@RequestMapping("/api/v1/contracts/{contractId}/bordereaux")
public class BordereauController {

    private final BordereauService service;

    public BordereauController(BordereauService service) {
        this.service = service;
    }

    @GetMapping
    public List<Bordereau> list(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                @RequestHeader(value = "X-EIA-Book", required = false) String book,
                                @PathVariable UUID contractId) {
        requireAssumedBookWhenPresent(book);
        return service.findAll(tenantId, contractId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Bordereau create(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                            @RequestHeader(value = "X-EIA-Book", required = false) String book,
                            @PathVariable UUID contractId,
                            @RequestBody CreateBordereauCommand command) {
        requireAssumedBookWhenPresent(book);
        ContractCommandRules.validate(command);
        return service.create(tenantId, contractId, command);
    }

    @PatchMapping("/{bordereauId}/status")
    public Bordereau updateStatus(
            @RequestHeader("X-EIA-Tenant") UUID tenantId,
            @RequestHeader(value = "X-EIA-Book", required = false) String book,
            @PathVariable UUID contractId,
            @PathVariable UUID bordereauId,
            @RequestBody UpdateBordereauStatusCommand command) {
        requireAssumedBookWhenPresent(book);
        ContractCommandRules.validate(command);
        return service.updateStatus(tenantId, contractId, bordereauId, command);
    }

    /**
     * The book wall is enforced on every bordereau call. A supplied book other
     * than ASSUMED is hidden as 404 so the response cannot confirm that an
     * assumed contract exists in another book (SPEC 3.6.2, WALL-06).
     */
    private static void requireAssumedBookWhenPresent(String raw) {
        if (raw != null && !raw.isBlank() && !BordereauClient.BOOK.equalsIgnoreCase(raw.trim())) {
            throw new ContractBookScopeException();
        }
    }
}
