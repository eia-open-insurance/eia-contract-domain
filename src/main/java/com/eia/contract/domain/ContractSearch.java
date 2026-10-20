package com.eia.contract.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Projects the expert's tenant-scoped contract list into the shared search
 * result shape.
 *
 * <p>Search is case-insensitive and matches the contract number or the contract
 * type. A blank query returns the most recently updated contracts, which is the
 * shell's empty-search landing state. The list is already tenant-scoped by the
 * expert; the tenant check below is a defensive second wall.</p>
 */
public final class ContractSearch {

    private static final Comparator<ContractDto> LATEST_FIRST =
            Comparator.comparing(ContractDto::updatedAt,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(ContractDto::id, Comparator.nullsLast(Comparator.naturalOrder()));

    private ContractSearch() {
    }

    public static SearchResult search(UUID tenantId, List<ContractDto> contracts, String query, int limit) {
        int effectiveLimit = SearchResult.effectiveLimit(limit);
        String term = query == null ? "" : query.trim();
        String needle = term.toLowerCase(Locale.ROOT);
        List<SearchResult.Item> items = (contracts == null ? List.<ContractDto>of() : contracts).stream()
                .filter(Objects::nonNull)
                .filter(contract -> tenantId.equals(contract.tenantId()))
                .filter(contract -> matches(contract, needle))
                .sorted(LATEST_FIRST)
                .limit(effectiveLimit)
                .map(ContractSearch::item)
                .toList();
        return new SearchResult(term, effectiveLimit, items);
    }

    private static boolean matches(ContractDto contract, String needle) {
        return needle.isEmpty()
                || contains(contract.contractNumber(), needle)
                || contains(contract.contractType(), needle);
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static SearchResult.Item item(ContractDto contract) {
        return new SearchResult.Item(
                contract.id(),
                "CONTRACT",
                contract.contractNumber(),
                SearchResult.secondary(contract.contractType(), contract.status()),
                contract.status(),
                "/contract?record=" + contract.id());
    }
}
