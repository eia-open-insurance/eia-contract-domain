package com.eia.contract.domain;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Public domain facade for contract security records. The currency
 * reconciliation, the over-collateralisation approval, and the lifecycle stay
 * in the expert; the domain keeps the tenant and ASSUMED book context on every
 * call.
 */
@Service
public class SecurityService {

    private final SecurityClient client;

    public SecurityService(SecurityClient client) {
        this.client = client;
    }

    public List<SecurityRecord> findAll(UUID tenantId, UUID contractId) {
        return client.findAll(tenantId, contractId);
    }

    public SecurityRecord create(UUID tenantId, UUID contractId, CreateSecurityCommand command) {
        return client.create(tenantId, contractId, command);
    }
}
