package com.eia.contract.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * HTTP client for the contract expert's technical-account lines.
 *
 * <p>Every call forwards the tenant and the ASSUMED book context so the expert
 * can enforce tenancy and the book wall independently. The expert is the
 * aggregate guard for the account status, the line ordering, and the
 * reconciliation against the account totals; this client translates its
 * structured error body into domain exceptions so the public API preserves the
 * expert's {@code 400}/{@code 404}/{@code 409} code and message. The expert
 * serves the lines as a {@code lines} array and a per-type {@code totals} map;
 * the client maps that read model to the public summary ({@code items} plus the
 * four financial buckets) and fills in any bucket the expert omits.</p>
 */
@Component
public class TechnicalAccountLineClient {

    public static final String BOOK = "ASSUMED";
    public static final String BOOK_HEADER = "X-EIA-Book";
    public static final String TENANT_HEADER = "X-EIA-Tenant";

    private static final String BASE_PATH =
            "/internal/v1/contracts/{contractId}/technical-accounts/{accountId}/lines";
    private static final String LINE_PATH = BASE_PATH + "/{lineId}";

    private static final Set<String> PREMIUM_TYPES = Set.of("PREMIUM");
    private static final Set<String> LOSS_TYPES =
            Set.of("LOSS", "PAID_LOSS", "OUTSTANDING_LOSS", "IBNR", "ALAE");
    private static final Set<String> COMMISSION_TYPES = Set.of("COMMISSION");

    private final RestClient client;
    private final ObjectMapper mapper;

    public TechnicalAccountLineClient(RestClient.Builder builder, ObjectMapper mapper,
                                      @Value("${eia.contract.expert-url:http://localhost:8085}") String baseUrl) {
        this.client = builder.baseUrl(baseUrl).build();
        this.mapper = mapper;
    }

    public LineSummary findAll(UUID tenantId, UUID contractId, UUID accountId) {
        try {
            ExpertLineSummary view = client.get()
                    .uri(uri -> uri.path(BASE_PATH).queryParam("tenantId", tenantId)
                            .build(contractId, accountId))
                    .header(TENANT_HEADER, tenantId.toString())
                    .header(BOOK_HEADER, BOOK)
                    .retrieve()
                    .body(ExpertLineSummary.class);
            return normalize(view);
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    public TechnicalAccountLine create(UUID tenantId, UUID contractId, UUID accountId,
                                       CreateTechnicalAccountLineCommand command) {
        try {
            TechnicalAccountLine line = client.post()
                    .uri(uri -> uri.path(BASE_PATH).queryParam("tenantId", tenantId)
                            .build(contractId, accountId))
                    .header(TENANT_HEADER, tenantId.toString())
                    .header(BOOK_HEADER, BOOK)
                    .body(command)
                    .retrieve()
                    .body(TechnicalAccountLine.class);
            if (line == null) {
                throw new ExpertIntegrationException("Contract expert returned an empty body", null);
            }
            return line;
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    public void delete(UUID tenantId, UUID contractId, UUID accountId, UUID lineId) {
        try {
            client.delete()
                    .uri(uri -> uri.path(LINE_PATH).queryParam("tenantId", tenantId)
                            .build(contractId, accountId, lineId))
                    .header(TENANT_HEADER, tenantId.toString())
                    .header(BOOK_HEADER, BOOK)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw translate(ex);
        } catch (ResourceAccessException ex) {
            throw unavailable(ex);
        }
    }

    private static LineSummary normalize(ExpertLineSummary view) {
        if (view == null) {
            return new LineSummary(List.of(), null);
        }
        List<TechnicalAccountLine> items = view.lines() == null ? List.of()
                : view.lines().stream().filter(Objects::nonNull).toList();
        LineSummary.Totals computed = computedTotals(items);
        WireTotals wire = wireTotals(view.totals());
        return new LineSummary(items, new LineSummary.Totals(
                wire == null || wire.premium() == null ? computed.premium() : wire.premium(),
                wire == null || wire.loss() == null ? computed.loss() : wire.loss(),
                wire == null || wire.commission() == null
                        ? computed.commission() : wire.commission(),
                wire == null || wire.other() == null ? computed.other() : wire.other()));
    }

    /**
     * Maps the expert's totals map into the public buckets. The expert keys the
     * map by line type, using either its four header components or the SPEC
     * detail types; every type rolls into exactly one bucket. A missing bucket
     * is left null so {@link #normalize} can derive it from the lines.
     */
    private static WireTotals wireTotals(Map<String, String> totals) {
        if (totals == null || totals.isEmpty()) {
            return null;
        }
        BigDecimal premium = null;
        BigDecimal loss = null;
        BigDecimal commission = null;
        BigDecimal other = null;
        for (Map.Entry<String, String> entry : totals.entrySet()) {
            String value = entry.getValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            BigDecimal amount;
            try {
                amount = new BigDecimal(value.trim());
            } catch (NumberFormatException ex) {
                continue;
            }
            String type = entry.getKey() == null
                    ? "" : entry.getKey().trim().toUpperCase(Locale.ROOT);
            if (PREMIUM_TYPES.contains(type)) {
                premium = premium == null ? amount : premium.add(amount);
            } else if (LOSS_TYPES.contains(type)) {
                loss = loss == null ? amount : loss.add(amount);
            } else if (COMMISSION_TYPES.contains(type)) {
                commission = commission == null ? amount : commission.add(amount);
            } else {
                other = other == null ? amount : other.add(amount);
            }
        }
        return new WireTotals(premium, loss, commission, other);
    }

    /**
     * Derives the four statement buckets from the lines. The expert normally
     * sends the totals, so this is the fallback for a projection that omits
     * them; every line type falls into premium, loss, commission, or other.
     */
    private static LineSummary.Totals computedTotals(List<TechnicalAccountLine> items) {
        BigDecimal premium = BigDecimal.ZERO;
        BigDecimal loss = BigDecimal.ZERO;
        BigDecimal commission = BigDecimal.ZERO;
        BigDecimal other = BigDecimal.ZERO;
        for (TechnicalAccountLine line : items) {
            if (line.amount() == null) {
                continue;
            }
            String type = line.lineType() == null
                    ? "" : line.lineType().trim().toUpperCase(Locale.ROOT);
            if (PREMIUM_TYPES.contains(type)) {
                premium = premium.add(line.amount());
            } else if (LOSS_TYPES.contains(type)) {
                loss = loss.add(line.amount());
            } else if (COMMISSION_TYPES.contains(type)) {
                commission = commission.add(line.amount());
            } else {
                other = other.add(line.amount());
            }
        }
        return new LineSummary.Totals(premium, loss, commission, other);
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
                    message == null ? "Technical account line not found" : message);
        }
        if (status == 409) {
            return new ContractConflictException(code == null ? "CONFLICT" : code,
                    message == null
                            ? "The technical account line conflicts with the account state"
                            : message);
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

    /** Wire shape of the expert's line summary, before the public normalization. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExpertLineSummary(
            UUID accountId,
            List<TechnicalAccountLine> lines,
            Map<String, String> totals
    ) {
    }

    /** Per-bucket wire totals; a null bucket means the expert omitted it. */
    private record WireTotals(
            BigDecimal premium,
            BigDecimal loss,
            BigDecimal commission,
            BigDecimal other
    ) {
    }
}
