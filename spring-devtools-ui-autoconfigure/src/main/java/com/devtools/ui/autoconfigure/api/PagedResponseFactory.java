package com.devtools.ui.autoconfigure.api;

import java.util.List;
import java.util.function.Predicate;

public final class PagedResponseFactory {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private PagedResponseFactory() {
    }

    public static <T> PagedResponse<T> from(List<T> items, String query, Integer offset, Integer limit,
                                            Predicate<T> matchesQuery) {
        String normalizedQuery = normalizeQuery(query);
        int resolvedOffset = Math.max(0, offset == null ? 0 : offset);
        int resolvedLimit = resolveLimit(limit);

        List<T> filteredItems = normalizedQuery.isEmpty()
                ? items
                : items.stream().filter(matchesQuery).toList();

        int total = filteredItems.size();
        int fromIndex = Math.min(resolvedOffset, total);
        int toIndex = Math.min(fromIndex + resolvedLimit, total);

        return new PagedResponse<>(filteredItems.subList(fromIndex, toIndex), total, fromIndex, resolvedLimit);
    }

    public static boolean containsIgnoreCase(String haystack, String query) {
        return haystack.toLowerCase(java.util.Locale.ROOT).contains(query);
    }

    public static String normalizeQuery(String query) {
        return query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static int resolveLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.min(MAX_LIMIT, Math.max(1, limit));
    }
}
