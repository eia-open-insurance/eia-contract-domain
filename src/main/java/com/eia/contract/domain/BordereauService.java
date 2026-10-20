package com.eia.contract.domain;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Public domain facade for contract bordereaux. Lifecycle rules and the
 * financial reconciliation stay in the expert; the domain keeps the tenant and
 * ASSUMED book context on every call.
 */
@Service
public class BordereauService {

    private final BordereauClient client;

    public BordereauService(BordereauClient client) {
        this.client = client;
    }

    public List<Bordereau> findAll(UUID tenantId, UUID contractId) {
        return client.findAll(tenantId, contractId);
    }

    public Bordereau create(UUID tenantId, UUID contractId, CreateBordereauCommand command) {
        return client.create(tenantId, contractId, command);
    }

    public Bordereau updateStatus(UUID tenantId, UUID contractId, UUID bordereauId,
                                  UpdateBordereauStatusCommand command) {
        return client.updateStatus(tenantId, contractId, bordereauId, command);
    }
}
