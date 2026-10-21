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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 * serves the lines as a {@code lines} array, a per-line-type {@code totals} map,
 * and the authoritative four-bucket {@code bucketTotals} map; the client rolls
 * that read model into the public summary ({@code items} plus the four financial
 * buckets and the detailed per-type map) and fills in any bucket the expert
 * omits.</p>
 */
@Component
public class TechnicalAccountLineClient {

    public static final String BOOK = "ASSUMED";
    public static final String BOOK_HEADER = "X-EIA-Book";
    public static final String TENANT_HEADER = "X-EIA-Tenant";

    private static final String BASE_PATH =
            "/internal/v1/contracts/{contractId}/technical-accounts/{accountId}/lines";
    private static final String LINE_PATH = BASE_PATH + "/{lineId}";

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
        Rollup computed = rollupLines(items);
        Rollup wire = rollupWire(view.totals());
        Rollup expertBuckets = rollupBuckets(view.bucketTotals());
        Map<String, BigDecimal> details = new LinkedHashMap<>(computed.details());
        details.putAll(wire.details());
        return new LineSummary(items, new LineSummary.Totals(
                firstNonNull(expertBuckets.premium(), wire.premium(), computed.premium()),
                firstNonNull(expertBuckets.loss(), wire.loss(), computed.loss()),
                firstNonNull(expertBuckets.commission(), wire.commission(), computed.commission()),
                firstNonNull(expertBuckets.other(), wire.other(), computed.other()),
                details));
    }

    private static BigDecimal firstNonNull(BigDecimal first, BigDecimal second, BigDecimal third) {
        return first != null ? first : second != null ? second : third;
    }

    /** @return the parsed amount, or null for a blank or non-numeric value */
    private static BigDecimal amount(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Reads the expert's authoritative bucket totals. They are already rolled
     * up by the expert's line-type mapping, so the domain prefers them over a
     * local roll-up; a missing bucket falls back to the detail totals and then
     * to the line rows.
     */
    private static Rollup rollupBuckets(Map<String, String> totals) {
        if (totals == null || totals.isEmpty()) {
            return Rollup.empty();
        }
        BigDecimal premium = null;
        BigDecimal loss = null;
        BigDecimal commission = null;
        BigDecimal other = null;
        for (Map.Entry<String, String> entry : totals.entrySet()) {
            BigDecimal amount = amount(entry.getValue());
            if (amount == null) {
                continue;
            }
            String bucket = TechnicalAccountLineTypes.normalize(entry.getKey());
            if (bucket == null) {
                continue;
            }
            switch (bucket) {
                case TechnicalAccountLineTypes.PREMIUM -> premium = add(premium, amount);
                case TechnicalAccountLineTypes.LEGACY_LOSS -> loss = add(loss, amount);
                case TechnicalAccountLineTypes.COMMISSION -> commission = add(commission, amount);
                case TechnicalAccountLineTypes.OTHER -> other = add(other, amount);
                default -> {
                    // A bucket outside the four names is not part of the contract.
                }
            }
        }
        return new Rollup(premium, loss, commission, other, Map.of());
    }

    /**
     * Maps the expert's detailed totals map into the public buckets and keeps
     * the per-line-type detail. The expert keys the map by line type; every
     * type rolls into exactly one bucket according to the expert's mapping. A
     * missing bucket is left null so {@link #normalize} can derive it from the
     * authoritative bucket totals or the lines.
     */
    private static Rollup rollupWire(Map<String, String> totals) {
        if (totals == null || totals.isEmpty()) {
            return Rollup.empty();
        }
        BigDecimal premium = null;
        BigDecimal loss = null;
        BigDecimal commission = null;
        BigDecimal other = null;
        Map<String, BigDecimal> details = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : totals.entrySet()) {
            BigDecimal amount = amount(entry.getValue());
            if (amount == null) {
                continue;
            }
            String type = TechnicalAccountLineTypes.normalize(entry.getKey());
            if (type == null) {
                continue;
            }
            details.merge(type, amount, BigDecimal::add);
            switch (TechnicalAccountLineTypes.bucketOf(type)) {
                case PREMIUM -> premium = add(premium, amount);
                case LOSS -> loss = add(loss, amount);
                case COMMISSION -> commission = add(commission, amount);
                case OTHER -> other = add(other, amount);
            }
        }
        return new Rollup(premium, loss, commission, other, details);
    }

    /**
     * Derives the four statement buckets and the detailed totals from the
     * lines. The expert normally sends the totals, so this is the fallback for
     * a projection that omits them; every line type falls into premium, loss,
     * commission, or other.
     */
    private static Rollup rollupLines(List<TechnicalAccountLine> items) {
        BigDecimal premium = BigDecimal.ZERO;
        BigDecimal loss = BigDecimal.ZERO;
        BigDecimal commission = BigDecimal.ZERO;
        BigDecimal other = BigDecimal.ZERO;
        Map<String, BigDecimal> details = new LinkedHashMap<>();
        for (TechnicalAccountLine line : items) {
            if (line.amount() == null) {
                continue;
            }
            String type = TechnicalAccountLineTypes.normalize(line.lineType());
            if (type == null) {
                type = TechnicalAccountLineTypes.OTHER;
            }
            details.merge(type, line.amount(), BigDecimal::add);
            switch (TechnicalAccountLineTypes.bucketOf(type)) {
                case PREMIUM -> premium = premium.add(line.amount());
                case LOSS -> loss = loss.add(line.amount());
                case COMMISSION -> commission = commission.add(line.amount());
                case OTHER -> other = other.add(line.amount());
            }
        }
        return new Rollup(premium, loss, commission, other, details);
    }

    private static BigDecimal add(BigDecimal current, BigDecimal amount) {
        return current == null ? amount : current.add(amount);
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
            Map<String, String> totals,
            Map<String, String> bucketTotals
    ) {
    }

    /**
     * Roll-up of the expert totals or the line rows. A null bucket means the
     * expert omitted it; the detailed map keeps the per-type amounts.
     */
    private record Rollup(
            BigDecimal premium,
            BigDecimal loss,
            BigDecimal commission,
            BigDecimal other,
            Map<String, BigDecimal> details
    ) {
        private static Rollup empty() {
            return new Rollup(null, null, null, null, Map.of());
        }
    }
}
