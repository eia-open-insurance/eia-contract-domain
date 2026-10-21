package com.eia.contract.domain;

import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Public domain facade for technical-account lines. The account status guard,
 * line sequencing, and reconciliation stay in the expert; the domain keeps the
 * tenant and ASSUMED book context on every call.
 */
@Service
public class TechnicalAccountLineService {

    private final TechnicalAccountLineClient client;

    public TechnicalAccountLineService(TechnicalAccountLineClient client) {
        this.client = client;
    }

    public LineSummary findAll(UUID tenantId, UUID contractId, UUID accountId) {
        return client.findAll(tenantId, contractId, accountId);
    }

    public TechnicalAccountLine create(UUID tenantId, UUID contractId, UUID accountId,
                                       CreateTechnicalAccountLineCommand command) {
        return client.create(tenantId, contractId, accountId, command);
    }

    public void delete(UUID tenantId, UUID contractId, UUID accountId, UUID lineId) {
        client.delete(tenantId, contractId, accountId, lineId);
    }
}
