package com.eia.contract.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * HTTP client for the contract expert.
 *
 * <p>The expert returns 400/404/409 with a structured body; this client translates
 * those status codes into domain exceptions so the controller layer never depends on
 * HTTP details.</p>
 */
@Component
public class ContractClient {

    private final RestClient client;

    public ContractClient(RestClient.Builder builder,
                          @Value("${eia.contract.expert-url:http://localhost:8085}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
    }

    public List<ContractDto> findAll(UUID tenantId) {
        try {
            List<ContractDto> contracts = client.get()
                    .uri(uri -> uri.path("/internal/v1/contracts")
                            .queryParam("tenantId", tenantId)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<ContractDto>>() {});
            return contracts == null ? List.of() : contracts;
        } catch (RestClientResponseException ex) {
            throw new ExpertIntegrationException(
                    "Contract expert list failed with status " + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    public ContractDto findById(UUID tenantId, UUID id) {
        try {
            ContractDto contract = client.get()
                    .uri(uri -> uri.path("/internal/v1/contracts/{id}")
                            .queryParam("tenantId", tenantId)
                            .build(id))
                    .retrieve()
                    .body(ContractDto.class);
            if (contract == null) {
                throw new ContractNotFoundException(id);
            }
            return contract;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ContractNotFoundException(id);
            }
            throw new ExpertIntegrationException(
                    "Contract expert read failed with status " + ex.getStatusCode().value(), ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    public ContractDto create(UUID tenantId, CreateContractCommand command, ContractType contractType) {
        ExpertCreateContractRequest request = new ExpertCreateContractRequest(
                tenantId,
                command.contractNumber(),
                contractType.name(),
                command.cedentPartyId(),
                command.reinsurerPartyId(),
                command.effectiveDate(),
                command.expirationDate(),
                command.currency(),
                ContractStatus.DRAFT.name());
        try {
            return client.post()
                    .uri("/internal/v1/contracts")
                    .body(request)
                    .retrieve()
                    .body(ContractDto.class);
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 409) {
                throw new DuplicateContractNumberException(command.contractNumber());
            }
            if (status == 400) {
                throw new ContractValidationException("EXPERT_VALIDATION_FAILED", null,
                        "The contract expert rejected the request: " + ex.getResponseBodyAsString());
            }
            throw new ExpertIntegrationException("Contract expert create failed with status " + status, ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    public ContractDto updateStatus(UUID tenantId, UUID id, ContractStatus status, int expectedVersion) {
        try {
            return client.patch()
                    .uri(uri -> uri.path("/internal/v1/contracts/{id}/status")
                            .queryParam("tenantId", tenantId)
                            .build(id))
                    .body(new ExpertStatusUpdate(status.name(), expectedVersion))
                    .retrieve()
                    .body(ContractDto.class);
        } catch (RestClientResponseException ex) {
            int code = ex.getStatusCode().value();
            if (code == 404) {
                throw new ContractNotFoundException(id);
            }
            if (code == 409) {
                throw new ConcurrentContractUpdateException(id);
            }
            if (code == 400) {
                throw new ContractValidationException("EXPERT_VALIDATION_FAILED", "status",
                        "The contract expert rejected the status change: " + ex.getResponseBodyAsString());
            }
            throw new ExpertIntegrationException("Contract expert update failed with status " + code, ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    private static ExpertIntegrationException unavailable(ResourceAccessException ex) {
        return new ExpertIntegrationException("Contract expert is unavailable", ex);
    }

    record ExpertCreateContractRequest(
            UUID tenantId,
            String contractNumber,
            String contractType,
            UUID cedentPartyId,
            UUID reinsurerPartyId,
            LocalDate effectiveDate,
            LocalDate expirationDate,
            String currency,
            String status
    ) {}

    record ExpertStatusUpdate(String status, int expectedVersion) {}
}
