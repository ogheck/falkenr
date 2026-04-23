package com.devtools.ui.autoconfigure;

record TimeTravelRequest(
        String instant,
        String zoneId,
        String reason,
        Integer durationMinutes
) {
}
