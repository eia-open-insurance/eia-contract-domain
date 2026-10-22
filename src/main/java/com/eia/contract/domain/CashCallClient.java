package com.eia.contract.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.UUID;

/**
 * HTTP client for the contract expert's cash calls.
 *
 * <p>Every call forwards the tenant and the ASSUMED book context so the expert
 * can enforce tenancy and the book wall independently. The expert is the
 * aggregate guard for call-number generation, the contract window, and the
 * lifecycle transitions; this client translates its structured error body into
 * domain exceptions so the public API preserves the expert's
 * {@code 400}/{@code 404}/{@code 409} code and message.</p>
 */
@Component
public class CashCallClient {

    public static final String BOOK = "ASSUMED";
    public static final String BOOK_HEADER = "X-EIA-Book";
    public static final String TENANT_HEADER = "X-EIA-Tenant";

    private static final String BASE_PATH = "/internal/v1/contracts/{contractId}/cash-calls";
    private static final String STATUS_PATH = BASE_PATH + "/{cashCallId}/status";
    private static final ParameterizedTypeReference<List<CashCall>> CASH_CALLS =
            new ParameterizedTypeReference<>() {};

    private final RestClient client;
    private final ObjectMapper mapper;

    public CashCallClient(RestClient.Builder builder, ObjectMapper mapper,
                          @Value("${eia.contract.expert-url:http://localhost:8085}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
        this.mapper = mapper;
    }

    public List<CashCall> findAll(UUID tenantId, UUID contractId) {
        try {
            List<CashCall> cashCalls = client.get()
                    .uri(uri -> uri.path(BASE_PATH).queryParam("tenantId", tenantId).build(contractId))
                    .header(TENANT_HEADER, tenantId.toString())
                    .header(BOOK_HEADER, BOOK)
                    .retrieve()
                    .body(CASH_CALLS);
            return cashCalls == null ? List.of() : cashCalls;
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    public CashCall create(UUID tenantId, UUID contractId, CreateCashCallCommand command) {
        try {
            CashCall cashCall = client.post()
                    .uri(uri -> uri.path(BASE_PATH).queryParam("tenantId", tenantId).build(contractId))
                    .header(TENANT_HEADER, tenantId.toString())
                    .header(BOOK_HEADER, BOOK)
                    .body(command)
                    .retrieve()
                    .body(CashCall.class);
            if (cashCall == null) {
                throw new ExpertIntegrationException("Contract expert returned an empty body", null);
            }
            return cashCall;
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    public CashCall updateStatus(UUID tenantId, UUID contractId, UUID cashCallId,
                                 UpdateCashCallStatusCommand command) {
        try {
            CashCall cashCall = client.patch()
                    .uri(uri -> uri.path(STATUS_PATH).queryParam("tenantId", tenantId)
                            .build(contractId, cashCallId))
                    .header(TENANT_HEADER, tenantId.toString())
                    .header(BOOK_HEADER, BOOK)
                    .body(statusPayload(command))
                    .retrieve()
                    .body(CashCall.class);
            if (cashCall == null) {
                throw new ExpertIntegrationException("Contract expert returned an empty body", null);
            }
            return cashCall;
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    /**
     * A settlement reference is recorded only when the transition settles the
     * call. The domain drops a reference supplied with any other target so the
     * expert never sees settlement evidence on an issuance or cancellation.
     */
    private static UpdateCashCallStatusCommand statusPayload(UpdateCashCallStatusCommand command) {
        if (command.reference() == null || settles(command.status())) {
            return command;
        }
        return new UpdateCashCallStatusCommand(command.status());
    }

    private static boolean settles(String status) {
        return status != null && "SETTLED".equalsIgnoreCase(status.trim());
    }

    private RuntimeException translate(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        String code = null;
        String message = null;
        try {
            JsonNode body = mapper.readTree(ex.getResponseBodyAsString());
            if (body != null) {
                code = body.path("code").isMissingNode() ? null : body.path("code").asText(null);
                message = body.path("message").isMissingNode() ? null : body.path("message").asText(null);
            }
        } catch (Exception ignored) {
            // A non-JSON error body falls back to the status-based defaults below.
        }
        if (status == 404) {
            return new ResourceNotFoundException(
                    message == null ? "Cash call not found" : message);
        }
        if (status == 409) {
            return new ContractConflictException(code == null ? "CONFLICT" : code,
                    message == null ? "The cash call conflicts with the contract state" : message);
        }
        if (status == 400) {
            return new ContractValidationException(code == null ? "EXPERT_VALIDATION_FAILED" : code,
                    null, message == null ? "The contract expert rejected the request" : message);
        }
        return new ExpertIntegrationException(
                "Contract expert returned status " + status, ex);
    }

    private static ExpertIntegrationException unavailable(ResourceAccessException ex) {
        return new ExpertIntegrationException("Contract expert is unavailable", ex);
    }
}
