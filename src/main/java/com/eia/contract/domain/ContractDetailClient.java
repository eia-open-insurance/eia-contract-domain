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
 * HTTP client for the contract expert's second-slice resources.
 *
 * <p>The expert is the aggregate guard: it re-validates every command and owns the
 * cross-row invariants. This client translates the expert's structured error body
 * into domain exceptions so the public API keeps its error codes.</p>
 */
@Component
public class ContractDetailClient {

    private static final ParameterizedTypeReference<List<ContractResources.Layer>> LAYERS =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<ContractResources.Participant>> PARTICIPANTS =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<ContractResources.Term>> TERMS =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<ContractResources.Cession>> CESSIONS =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<ContractResources.Commutation>> COMMUTATIONS =
            new ParameterizedTypeReference<>() {};

    private final RestClient client;
    private final ObjectMapper mapper;

    public ContractDetailClient(RestClient.Builder builder, ObjectMapper mapper,
                                @Value("${eia.contract.expert-url:http://localhost:8085}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
        this.mapper = mapper;
    }

    public List<ContractResources.Layer> findLayers(UUID tenantId, UUID contractId) {
        return getList("/internal/v1/contracts/{contractId}/layers", tenantId, LAYERS, contractId);
    }

    public ContractResources.TowerSummary layerSummary(UUID tenantId, UUID contractId) {
        return get("/internal/v1/contracts/{contractId}/layers/summary", tenantId,
                ContractResources.TowerSummary.class, contractId);
    }

    public ContractResources.Layer createLayer(UUID tenantId, UUID contractId,
                                               ContractCommands.CreateLayer command) {
        return post("/internal/v1/contracts/{contractId}/layers", tenantId, command,
                ContractResources.Layer.class, contractId);
    }

    public List<ContractResources.Participant> findParticipants(UUID tenantId, UUID contractId) {
        return getList("/internal/v1/contracts/{contractId}/participants", tenantId, PARTICIPANTS, contractId);
    }

    public ContractResources.Participant createParticipant(UUID tenantId, UUID contractId,
                                                           ContractCommands.CreateParticipant command) {
        return post("/internal/v1/contracts/{contractId}/participants", tenantId, command,
                ContractResources.Participant.class, contractId);
    }

    public List<ContractResources.Term> findTerms(UUID tenantId, UUID contractId) {
        return getList("/internal/v1/contracts/{contractId}/terms", tenantId, TERMS, contractId);
    }

    public ContractResources.TermRequirements termRequirements(UUID tenantId, UUID contractId) {
        return get("/internal/v1/contracts/{contractId}/terms/requirements", tenantId,
                ContractResources.TermRequirements.class, contractId);
    }

    public ContractResources.Term createTerm(UUID tenantId, UUID contractId,
                                             ContractCommands.CreateTerm command) {
        return post("/internal/v1/contracts/{contractId}/terms", tenantId, command,
                ContractResources.Term.class, contractId);
    }

    public List<ContractResources.Cession> findCessions(UUID tenantId, UUID contractId) {
        return getList("/internal/v1/contracts/{contractId}/cessions", tenantId, CESSIONS, contractId);
    }

    public ContractResources.Cession createCession(UUID tenantId, UUID contractId,
                                                   ContractCommands.CreateCession command) {
        return post("/internal/v1/contracts/{contractId}/cessions", tenantId, command,
                ContractResources.Cession.class, contractId);
    }

    public List<ContractResources.Commutation> findCommutations(UUID tenantId, UUID contractId) {
        return getList("/internal/v1/contracts/{contractId}/commutations", tenantId, COMMUTATIONS, contractId);
    }

    public ContractResources.Commutation createCommutation(UUID tenantId, UUID contractId,
                                                           ContractCommands.CreateCommutation command) {
        return post("/internal/v1/contracts/{contractId}/commutations", tenantId, command,
                ContractResources.Commutation.class, contractId);
    }

    public ContractResources.Commutation approveCommutation(UUID tenantId, UUID contractId,
                                                            UUID commutationId,
                                                            ContractCommands.ApproveCommutation command) {
        return post("/internal/v1/contracts/{contractId}/commutations/{commutationId}/approve",
                tenantId, command, ContractResources.Commutation.class, contractId, commutationId);
    }

    public ContractResources.CommutationExecution executeCommutation(UUID tenantId, UUID contractId,
                                                                     UUID commutationId,
                                                                     ContractCommands.ExecuteCommutation command) {
        return post("/internal/v1/contracts/{contractId}/commutations/{commutationId}/execute",
                tenantId, command, ContractResources.CommutationExecution.class, contractId, commutationId);
    }

    public ContractResources.BindEligibility bindEligibility(UUID tenantId, UUID contractId) {
        return get("/internal/v1/contracts/{contractId}/bind-eligibility", tenantId,
                ContractResources.BindEligibility.class, contractId);
    }

    public ContractResources.BindResult bind(UUID tenantId, UUID contractId,
                                             ContractCommands.Bind command) {
        return post("/internal/v1/contracts/{contractId}/bind", tenantId, command,
                ContractResources.BindResult.class, contractId);
    }

    private <T> T get(String path, UUID tenantId, Class<T> type, Object... vars) {
        try {
            T body = client.get()
                    .uri(uri -> uri.path(path).queryParam("tenantId", tenantId).build(vars))
                    .retrieve()
                    .body(type);
            if (body == null) {
                throw new ExpertIntegrationException("Contract expert returned an empty body", null);
            }
            return body;
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    private <T> List<T> getList(String path, UUID tenantId,
                                ParameterizedTypeReference<List<T>> type, Object... vars) {
        try {
            List<T> body = client.get()
                    .uri(uri -> uri.path(path).queryParam("tenantId", tenantId).build(vars))
                    .retrieve()
                    .body(type);
            return body == null ? List.of() : body;
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    private <T> T post(String path, UUID tenantId, Object body, Class<T> type, Object... vars) {
        try {
            T result = client.post()
                    .uri(uri -> uri.path(path).queryParam("tenantId", tenantId).build(vars))
                    .body(body)
                    .retrieve()
                    .body(type);
            if (result == null) {
                throw new ExpertIntegrationException("Contract expert returned an empty body", null);
            }
            return result;
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
            return new ResourceNotFoundException(message == null ? "Contract resource not found" : message);
        }
        if (status == 409) {
            return new ContractConflictException(code == null ? "CONFLICT" : code,
                    message == null ? "The request conflicts with the contract state" : message);
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
