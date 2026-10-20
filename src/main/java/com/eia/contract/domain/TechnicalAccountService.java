package com.eia.contract.domain;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Public domain facade for contract technical accounts. Production rules and
 * the balance reconciliation stay in the expert; the domain keeps the tenant
 * and ASSUMED book context on every call.
 */
@Service
public class TechnicalAccountService {

    private final TechnicalAccountClient client;

    public TechnicalAccountService(TechnicalAccountClient client) {
        this.client = client;
    }

    public List<TechnicalAccount> findAll(UUID tenantId, UUID contractId) {
        return client.findAll(tenantId, contractId);
    }

    public TechnicalAccount create(UUID tenantId, UUID contractId,
                                   CreateTechnicalAccountCommand command) {
        return client.create(tenantId, contractId, command);
    }

    public TechnicalAccount updateStatus(UUID tenantId, UUID contractId, UUID accountId,
                                         UpdateTechnicalAccountStatusCommand command) {
        return client.updateStatus(tenantId, contractId, accountId, command);
    }
}
