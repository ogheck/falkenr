package com.devtools.ui.autoconfigure.api;

import java.util.List;

public record PagedResponse<T>(
        List<T> items,
        int total,
        int offset,
        int limit
) {
}
