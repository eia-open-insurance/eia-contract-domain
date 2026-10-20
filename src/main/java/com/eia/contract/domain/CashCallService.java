package com.eia.contract.domain;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Public domain facade for contract cash calls. Call-number generation, the
 * contract window, and the lifecycle stay in the expert; the domain keeps the
 * tenant and ASSUMED book context on every call.
 */
@Service
public class CashCallService {

    private final CashCallClient client;

    public CashCallService(CashCallClient client) {
        this.client = client;
    }

    public List<CashCall> findAll(UUID tenantId, UUID contractId) {
        return client.findAll(tenantId, contractId);
    }

    public CashCall create(UUID tenantId, UUID contractId, CreateCashCallCommand command) {
        return client.create(tenantId, contractId, command);
    }

    public CashCall updateStatus(UUID tenantId, UUID contractId, UUID cashCallId,
                                 UpdateCashCallStatusCommand command) {
        return client.updateStatus(tenantId, contractId, cashCallId, command);
    }
}
