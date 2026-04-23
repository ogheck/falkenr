package com.devtools.ui.autoconfigure;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

class MutableDevToolsClock extends Clock {

    private final AtomicReference<Instant> fixedInstant = new AtomicReference<>();
    private final AtomicReference<ZoneId> zone = new AtomicReference<>(ZoneId.systemDefault());
    private final AtomicReference<OverrideMetadata> metadata = new AtomicReference<>();

    @Override
    public ZoneId getZone() {
        expireIfNecessary();
        return zone.get();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        MutableDevToolsClock clock = new MutableDevToolsClock();
        clock.zone.set(zone);
        clock.fixedInstant.set(fixedInstant.get());
        clock.metadata.set(metadata.get());
        return clock;
    }

    @Override
    public Instant instant() {
        expireIfNecessary();
        Instant override = fixedInstant.get();
        return override != null ? override : Instant.now();
    }

    boolean isOverridden() {
        expireIfNecessary();
        return fixedInstant.get() != null;
    }

    void set(Instant instant, ZoneId zoneId, String actor, String reason, Integer durationMinutes) {
        Instant now = Instant.now();
        fixedInstant.set(instant);
        zone.set(zoneId);
        metadata.set(new OverrideMetadata(
                actor == null || actor.isBlank() ? "local-operator" : actor.trim(),
                reason == null || reason.isBlank() ? "" : reason.trim(),
                now,
                durationMinutes == null || durationMinutes <= 0 ? null : now.plus(Duration.ofMinutes(durationMinutes))
        ));
    }

    void reset() {
        fixedInstant.set(null);
        zone.set(ZoneId.systemDefault());
        metadata.set(null);
    }

    String overrideReason() {
        expireIfNecessary();
        OverrideMetadata overrideMetadata = metadata.get();
        return overrideMetadata == null ? null : overrideMetadata.reason();
    }

    String overriddenBy() {
        expireIfNecessary();
        OverrideMetadata overrideMetadata = metadata.get();
        return overrideMetadata == null ? null : overrideMetadata.actor();
    }

    String overriddenAt() {
        expireIfNecessary();
        OverrideMetadata overrideMetadata = metadata.get();
        return overrideMetadata == null ? null : overrideMetadata.overriddenAt().toString();
    }

    String expiresAt() {
        expireIfNecessary();
        OverrideMetadata overrideMetadata = metadata.get();
        return overrideMetadata == null || overrideMetadata.expiresAt() == null ? null : overrideMetadata.expiresAt().toString();
    }

    private void expireIfNecessary() {
        OverrideMetadata overrideMetadata = metadata.get();
        if (overrideMetadata == null || overrideMetadata.expiresAt() == null) {
            return;
        }
        if (!overrideMetadata.expiresAt().isAfter(Instant.now())) {
            reset();
        }
    }

    private record OverrideMetadata(
            String actor,
            String reason,
            Instant overriddenAt,
            Instant expiresAt
    ) {
    }
}
