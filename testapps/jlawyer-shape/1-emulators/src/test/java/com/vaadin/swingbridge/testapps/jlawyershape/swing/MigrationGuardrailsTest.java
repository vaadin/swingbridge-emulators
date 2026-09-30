package com.vaadin.swingbridge.testapps.jlawyershape.swing;

import com.vaadin.swingbridge.migration.guardrails.MigrationGuardrails;
import org.junit.jupiter.api.Test;

class MigrationGuardrailsTest {
    @Test
    void guardrails() {
        // Every root that compiles into this jar, the vendored j-lawyer slice and its mocks included.
        new MigrationGuardrails("com.vaadin.swingbridge.testapps.jlawyershape",
                "com.jdimension.jlawyer", "com.formdev.flatlaf", "org.apache.log4j")
                .run();
    }
}
