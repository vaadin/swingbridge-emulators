package com.ca.ui;

import com.vaadin.swingbridge.migration.guardrails.MigrationGuardrails;
import org.junit.jupiter.api.Test;

class MigrationGuardrailsTest {
    @Test
    void guardrails() {
        new MigrationGuardrails("com.ca", "com.gt", "com.vaadin.swingbridge.fixture")
                .run();
    }
}
