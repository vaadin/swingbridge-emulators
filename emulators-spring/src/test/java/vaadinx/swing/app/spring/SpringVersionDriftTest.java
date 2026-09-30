/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package vaadinx.swing.app.spring;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.SpringVersion;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The durable version-drift gate. Until this module existed there was nowhere in the reactor to
 * put one — no module could take a Spring dependency — so the only check lived in
 * {@code testapps/crud/1-emulators}, which every round rewrites and which only CI's {@code -Pkit}
 * builds. This runs on a plain {@code ./mvnw -C verify}.
 *
 * <p>It guards two different things that drift independently, and the second is the one with
 * migrator-facing consequences.
 */
public class SpringVersionDriftTest {

    /**
     * The Spring Boot this module's pom names must be the one actually resolved. A mismatch is not
     * a build error by itself — it just means we compile against one Boot and Vaadin's starter
     * brings another, which surfaces as a {@code NoSuchMethodError} in somebody's app.
     */
    @Test
    public void pomVersionMatchesTheResolvedSpringBoot() throws IOException {
        Properties build = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/build.properties")) {
            assertNotNull(in, "build.properties missing — is testResource filtering still on?");
            build.load(in);
        }
        assertEquals(build.getProperty("spring-boot.version"), SpringBootVersion.getVersion(),
                "The pom's ${spring-boot.version} and the Spring Boot on the classpath disagree. "
                        + "Re-derive it: ./mvnw -C -pl emulators-spring dependency:tree -Dincludes=org.springframework.boot");
    }

    /**
     * The generation pair the migration docs name. `build-wiring.md`'s bootstrap chooser states
     * that Vaadin 25 requires Spring Boot 4 / Framework 7 / Jakarta EE 11, and its population
     * table and Spring-version cliff are written against that pair — so if Vaadin moves to the
     * next generation, all three are wrong at once, and silently.
     *
     * <p>Deliberately asserted on the MAJOR only: a 4.1 → 4.2 bump changes nothing a migrator was
     * told, and a gate that fires on it would be turned off.
     */
    @Test
    public void vaadinStillResolvesTheDocumentedSpringGeneration() {
        String boot = SpringBootVersion.getVersion();
        String framework = SpringVersion.getVersion();
        assertNotNull(boot, "Spring Boot version unreadable");
        assertNotNull(framework, "Spring Framework version unreadable");

        String message = "Vaadin now resolves Spring Boot " + boot + " / Framework " + framework
                + ", not the 4.x / 7.x the migration docs promise. Every migrator-facing statement of"
                + " the supported version set has to move together: the consent fork's support"
                + " boundary, build-wiring.md's Spring-version cliff, and the chooser's population"
                + " table.";
        assertEquals("4", boot.split("\\.")[0], message);
        assertEquals("7", framework.split("\\.")[0], message);
    }
}
