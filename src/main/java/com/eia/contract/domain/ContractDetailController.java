package com.eia.contract.domain;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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
 * Public contract detail API: layers, participants, terms, security, cessions,
 * commutations, and bind.
 *
 * <p>Every path follows SPEC 11.3.10 under {@code /api/v1/contracts/{contractId}}.
 * The tenant comes from {@code X-EIA-Tenant}; the domain validates the command shape
 * and the expert enforces the aggregate invariants.</p>
 */
@RestController
@RequestMapping("/api/v1/contracts/{contractId}")
public class ContractDetailController {

    private final ContractDetailClient client;

    public ContractDetailController(ContractDetailClient client) {
        this.client = client;
    }

    // ---------------------------------------------------------------- layers

    @GetMapping("/layers")
    public List<ContractResources.Layer> layers(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                                @PathVariable UUID contractId) {
        return client.findLayers(tenantId, contractId);
    }

    @GetMapping("/layers/summary")
    public ContractResources.TowerSummary layerSummary(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                                       @PathVariable UUID contractId) {
        return client.layerSummary(tenantId, contractId);
    }

    @PostMapping("/layers")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractResources.Layer createLayer(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                               @PathVariable UUID contractId,
                                               @RequestBody ContractCommands.CreateLayer command) {
        ContractCommandRules.validate(command);
        return client.createLayer(tenantId, contractId, command);
    }

    // ---------------------------------------------------------- participants

    @GetMapping("/participants")
    public List<ContractResources.Participant> participants(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                                            @PathVariable UUID contractId) {
        return client.findParticipants(tenantId, contractId);
    }

    @PostMapping("/participants")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractResources.Participant createParticipant(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                                           @PathVariable UUID contractId,
                                                           @RequestBody ContractCommands.CreateParticipant command) {
        ContractCommandRules.validate(command);
        return client.createParticipant(tenantId, contractId, command);
    }

    // ----------------------------------------------------------------- terms

    @GetMapping("/terms")
    public List<ContractResources.Term> terms(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                              @PathVariable UUID contractId) {
        return client.findTerms(tenantId, contractId);
    }

    @GetMapping("/terms/requirements")
    public ContractResources.TermRequirements termRequirements(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                                               @PathVariable UUID contractId) {
        return client.termRequirements(tenantId, contractId);
    }

    @PostMapping("/terms")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractResources.Term createTerm(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                             @PathVariable UUID contractId,
                                             @RequestBody ContractCommands.CreateTerm command) {
        ContractCommandRules.validate(command);
        return client.createTerm(tenantId, contractId, command);
    }

    // -------------------------------------------------------------- security

    @GetMapping("/security")
    public List<ContractResources.Security> security(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                                     @PathVariable UUID contractId) {
        return client.findSecurity(tenantId, contractId);
    }

    @PostMapping("/security")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractResources.Security createSecurity(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                                     @PathVariable UUID contractId,
                                                     @RequestBody ContractCommands.CreateSecurity command) {
        ContractCommandRules.validate(command);
        return client.createSecurity(tenantId, contractId, command);
    }

    // --------------------------------------------------------------- cessions

    @GetMapping("/cessions")
    public List<ContractResources.Cession> cessions(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                                    @PathVariable UUID contractId) {
        return client.findCessions(tenantId, contractId);
    }

    @PostMapping("/cessions")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractResources.Cession createCession(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                                   @PathVariable UUID contractId,
                                                   @RequestBody ContractCommands.CreateCession command) {
        ContractCommandRules.validate(command);
        return client.createCession(tenantId, contractId, command);
    }

    // ------------------------------------------------------------ commutations

    @GetMapping("/commutations")
    public List<ContractResources.Commutation> commutations(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                                            @PathVariable UUID contractId) {
        return client.findCommutations(tenantId, contractId);
    }

    @PostMapping("/commutations")
    @ResponseStatus(HttpStatus.CREATED)
    public ContractResources.Commutation createCommutation(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                                           @PathVariable UUID contractId,
                                                           @RequestBody ContractCommands.CreateCommutation command) {
        ContractCommandRules.validate(command);
        return client.createCommutation(tenantId, contractId, command);
    }

    @PostMapping("/commutations/{commutationId}/approve")
    public ContractResources.Commutation approveCommutation(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                                            @PathVariable UUID contractId,
                                                            @PathVariable UUID commutationId,
                                                            @Valid @RequestBody ContractCommands.ApproveCommutation command) {
        return client.approveCommutation(tenantId, contractId, commutationId, command);
    }

    @PostMapping("/commutations/{commutationId}/execute")
    public ContractResources.CommutationExecution executeCommutation(
            @RequestHeader("X-EIA-Tenant") UUID tenantId,
            @PathVariable UUID contractId,
            @PathVariable UUID commutationId,
            @RequestBody(required = false) ContractCommands.ExecuteCommutation command) {
        return client.executeCommutation(tenantId, contractId, commutationId, command);
    }

    // ------------------------------------------------------------------ bind

    @GetMapping("/bind-eligibility")
    public ContractResources.BindEligibility bindEligibility(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                                             @PathVariable UUID contractId) {
        return client.bindEligibility(tenantId, contractId);
    }

    @PostMapping("/bind")
    public ContractResources.BindResult bind(@RequestHeader("X-EIA-Tenant") UUID tenantId,
                                             @PathVariable UUID contractId,
                                             @Valid @RequestBody ContractCommands.Bind command) {
        ContractCommandRules.validate(command);
        return client.bind(tenantId, contractId, command);
    }
}
