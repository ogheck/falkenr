package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.model.SessionShareTokenDescriptor;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class LocalAgentSessionStoreSecretsMaskingTest {

    @Test
    void masksSessionLinksAndTokenPreviewsWhenEnabled() {
        DevToolsUiProperties.RemoteSettings remote = new DevToolsUiProperties.RemoteSettings();
        DevToolsUiProperties.SecretsSettings secrets = new DevToolsUiProperties.SecretsSettings();
        secrets.setMaskSessionSecrets(true);
        LocalAgentSessionStore store = new LocalAgentSessionStore(
                Clock.fixed(Instant.parse("2026-04-09T12:00:00Z"), ZoneOffset.UTC),
                remote,
                secrets,
                new DefaultDevToolsDataPolicy(new DevToolsUiProperties.PolicySettings())
        );

        store.attach(new RemoteSessionAttachRequest("owner", false));
        SessionShareTokenDescriptor share = store.issueShareToken("viewer");
        var snapshot = store.snapshot();

        assertThat(share.tokenPreview()).isEqualTo("[masked]");
        assertThat(share.shareUrl()).contains("token=share_");
        assertThat(snapshot.shareUrl()).doesNotContain("token=");
        assertThat(snapshot.lastIssuedShareTokenPreview()).isEqualTo("[masked]");
        assertThat(snapshot.ownerTokenPreview()).isEqualTo("[masked]");
    }
}
