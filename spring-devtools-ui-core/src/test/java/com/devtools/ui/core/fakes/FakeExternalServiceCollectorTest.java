package com.devtools.ui.core.fakes;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FakeExternalServiceCollectorTest {

    @Test
    void collectReflectsEnabledStateFromStore() {
        FakeExternalServiceStore store = new FakeExternalServiceStore();
        store.setEnabled("github", true);

        FakeExternalServiceCollector collector = new FakeExternalServiceCollector(store);

        assertThat(collector.collect())
                .filteredOn(service -> service.serviceId().equals("github"))
                .singleElement()
                .satisfies(service -> {
                    assertThat(service.enabled()).isTrue();
                    assertThat(service.routes()).contains("POST /_dev/fake/github/webhooks");
                });
    }
}
