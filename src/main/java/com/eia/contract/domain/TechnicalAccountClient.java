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
 * HTTP client for the contract expert's technical accounts.
 *
 * <p>Every call forwards the tenant and the ASSUMED book context so the expert
 * can enforce tenancy and the book wall independently. The expert is the
 * aggregate guard for period overlap, the contract window, the balance
 * reconciliation, and lifecycle transitions; this client translates its
 * structured error body into domain exceptions so the public API preserves the
 * expert's {@code 400}/{@code 404}/{@code 409} code and message.</p>
 */
@Component
public class TechnicalAccountClient {

    public static final String BOOK = "ASSUMED";
    public static final String BOOK_HEADER = "X-EIA-Book";
    public static final String TENANT_HEADER = "X-EIA-Tenant";

    private static final String BASE_PATH = "/internal/v1/contracts/{contractId}/technical-accounts";
    private static final String STATUS_PATH =
            "/internal/v1/contracts/{contractId}/technical-accounts/{accountId}/status";
    private static final ParameterizedTypeReference<List<TechnicalAccount>> ACCOUNTS =
            new ParameterizedTypeReference<>() {};

    private final RestClient client;
    private final ObjectMapper mapper;

    public TechnicalAccountClient(RestClient.Builder builder, ObjectMapper mapper,
                                  @Value("${eia.contract.expert-url:http://localhost:8085}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
        this.mapper = mapper;
    }

    public List<TechnicalAccount> findAll(UUID tenantId, UUID contractId) {
        try {
            List<TechnicalAccount> accounts = client.get()
                    .uri(uri -> uri.path(BASE_PATH).queryParam("tenantId", tenantId).build(contractId))
                    .header(TENANT_HEADER, tenantId.toString())
                    .header(BOOK_HEADER, BOOK)
                    .retrieve()
                    .body(ACCOUNTS);
            return accounts == null ? List.of() : accounts;
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    public TechnicalAccount create(UUID tenantId, UUID contractId,
                                   CreateTechnicalAccountCommand command) {
        try {
            TechnicalAccount account = client.post()
                    .uri(uri -> uri.path(BASE_PATH).queryParam("tenantId", tenantId).build(contractId))
                    .header(TENANT_HEADER, tenantId.toString())
                    .header(BOOK_HEADER, BOOK)
                    .body(command)
                    .retrieve()
                    .body(TechnicalAccount.class);
            if (account == null) {
                throw new ExpertIntegrationException("Contract expert returned an empty body", null);
            }
            return account;
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    public TechnicalAccount updateStatus(UUID tenantId, UUID contractId, UUID accountId,
                                         UpdateTechnicalAccountStatusCommand command) {
        try {
            TechnicalAccount account = client.patch()
                    .uri(uri -> uri.path(STATUS_PATH).queryParam("tenantId", tenantId)
                            .build(contractId, accountId))
                    .header(TENANT_HEADER, tenantId.toString())
                    .header(BOOK_HEADER, BOOK)
                    .body(command)
                    .retrieve()
                    .body(TechnicalAccount.class);
            if (account == null) {
                throw new ExpertIntegrationException("Contract expert returned an empty body", null);
            }
            return account;
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
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
                    message == null ? "Technical account not found" : message);
        }
        if (status == 409) {
            return new ContractConflictException(code == null ? "CONFLICT" : code,
                    message == null ? "The technical account conflicts with the contract state" : message);
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
