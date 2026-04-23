package com.devtools.ui.autoconfigure;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RelayTokenCodecTest {

    @Test
    void encodeProducesACompactEncryptedTokenAndPreview() {
        RelayTokenCodec codec = new RelayTokenCodec(Clock.fixed(Instant.parse("2026-04-08T16:00:00Z"), ZoneOffset.UTC));

        String token = codec.encode(
                "session-123",
                "pair-debugger",
                List.of("owner", "viewer"),
                "2026-04-09T00:00:00Z"
        );

        assertThat(token).contains(".");
        assertThat(token).doesNotContain("session-123");
        assertThat(token).doesNotContain("pair-debugger");
        assertThat(codec.preview(token)).contains("…");
    }
}
