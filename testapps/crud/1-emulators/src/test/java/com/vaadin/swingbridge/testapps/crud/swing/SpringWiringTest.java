/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: 0BSD
 *
 * A seed file: copy it into your app and license the result as you choose. This
 * notice need not be kept.
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
 * OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package com.vaadin.swingbridge.testapps.crud.swing;

import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.spring.SpringServlet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import vaadinx.swing.app.SwingBridgeEmulatorsBootstrap;
import vaadinx.swing.app.spring.SwingBridgeSpringServlet;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Guards the Spring wiring a migrated app needs, every piece of which fails <em>silently</em> when
 * lost: the app boots, serves pages and looks healthy right up to the first modal dialog — or, for
 * the version check, right up to {@code java -jar}. Keep it in your app's test suite.
 */
@SpringBootTest
class SpringWiringTest {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("the registered Vaadin servlet is SB-Emulators', not the one Vaadin's auto-configuration would supply")
    void swingBridgeServletReplacesTheAutoConfiguredOne() {
        // Filtered by servlet type, not by bean count: Spring MVC contributes a
        // DispatcherServlet registration of its own, so a Vaadin + Spring app always has two.
        List<SpringServlet> vaadinServlets = context.getBeansOfType(ServletRegistrationBean.class)
                .values().stream()
                .map(ServletRegistrationBean::getServlet)
                .filter(SpringServlet.class::isInstance)
                .map(SpringServlet.class::cast)
                .toList();
        assertEquals(1, vaadinServlets.size(), "expected exactly one Vaadin servlet, found " + vaadinServlets);
        assertInstanceOf(SwingBridgeSpringServlet.class, vaadinServlets.get(0),
                "Vaadin's own SpringServlet is registered instead of SwingBridgeSpringServlet, so the"
                        + " session lock is not virtual-thread-aware and every blocking modal dialog"
                        + " will fail. Is swingbridge-emulators-spring on the classpath?");
    }

    @Test
    @DisplayName("SwingBridgeEmulatorsBootstrap and AppErrorHandler are each registered exactly once")
    void listenersAreRegisteredExactlyOnce() {
        // A second copy usually means the class is both a bean and listed in META-INF/services,
        // the natural thing to carry over from a Vaadin Boot app — every per-UI step then runs twice.
        for (Class<?> listener : List.of(SwingBridgeEmulatorsBootstrap.class, AppErrorHandler.class)) {
            long found = context.getBeansOfType(VaadinServiceInitListener.class).values().stream()
                    .filter(listener::isInstance)
                    .count();
            assertEquals(1, found, "expected exactly one " + listener.getSimpleName()
                    + "; is it also listed in META-INF/services?");
        }
    }

    /**
     * The pom imports no Spring BOM, so its {@code spring-boot.version} is hand-maintained while
     * most Spring artifacts arrive versioned by {@code vaadin-spring-boot-starter}. A Vaadin upgrade
     * can move one and not the other, and neither half of the mismatch is a build error.
     */
    @Test
    @DisplayName("the declared spring-boot.version is the Spring Boot the Vaadin starter resolved")
    void declaredSpringBootVersionMatchesTheResolvedOne() throws IOException {
        Properties build = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/build.properties")) {
            assertNotNull(in, "build.properties is missing: is the <testResources> block still in the pom?");
            build.load(in);
        }
        String declared = build.getProperty("spring-boot.version");
        assertNotNull(declared, "build.properties carries no spring-boot.version");
        assertEquals(declared, SpringBootVersion.getVersion(),
                "the pom declares spring-boot.version=" + declared + " but vaadin-spring-boot-starter"
                        + " resolved " + SpringBootVersion.getVersion() + ". Re-derive the property:"
                        + " ./mvnw -C dependency:tree -Dincludes=org.springframework.boot");
    }
}
