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

import com.vaadin.flow.spring.RootMappedCondition;
import com.vaadin.flow.spring.SpringBootAutoConfiguration;
import com.vaadin.flow.spring.SpringServlet;
import com.vaadin.flow.spring.VaadinConfigurationProperties;
import jakarta.servlet.MultipartConfigElement;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import vaadinx.swing.app.SwingBridgeEmulatorsBootstrap;

/**
 * Wires a Spring Boot app for SB-Emulators, so a migrated app's host app carries **no** servlet,
 * no servlet registration and no bootstrap bean of its own. Discovered through
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports};
 * nothing has to be imported or scanned.
 *
 * <p><b>This is the one place in SB-Emulators that self-registers</b>, under
 * R_no_spi_selfregister's framework-dedicated-module exception. Its three limbs are what the
 * class is shaped by: this module exists only for Spring, registration goes through Spring's own
 * discovery rather than {@code META-INF/services} (which is precisely what conflicts with
 * Spring's bean discovery), and every bean below is {@link ConditionalOnMissingBean}, so an app
 * that declares its own still wins. See D_library_servlet.
 *
 * <p>Ordered {@code before} Vaadin's own {@link SpringBootAutoConfiguration}: its servlet
 * registration is itself {@code @ConditionalOnMissingBean}, so registering first is what makes it
 * back off in favour of ours.
 */
@AutoConfiguration(before = SpringBootAutoConfiguration.class)
public class SwingBridgeAutoConfiguration {

    /**
     * Refuses a deployment that serves Vaadin requests on virtual threads
     * (R_match_swing_errors case (10), D_virtual_request_threads) at context refresh — the
     * earliest knowable moment, and reachable only from a Spring-aware component because the
     * switch is a Spring property.
     *
     * <p>Without this the refusal comes only at the first peer→Swing callback, from
     * {@code callSwing}: the loom runner supports platform request threads only, and the
     * deployment is wrong from startup on.
     */
    public SwingBridgeAutoConfiguration(Environment environment) {
        if (environment.getProperty("spring.threads.virtual.enabled", Boolean.class, Boolean.FALSE)) {
            throw new IllegalStateException(
                    "SwingBridge Emulators cannot run with spring.threads.virtual.enabled=true: "
                            + "Vaadin request threads must be platform threads, because the blocking-dialog "
                            + "machinery mounts its own virtual threads on them as carriers. "
                            + "Set spring.threads.virtual.enabled=false (the default) in application.properties.");
        }
    }

    /**
     * Replaces the servlet registration Vaadin's own auto-configuration would publish, with one
     * whose servlet wraps the session lock.
     *
     * <p>Built through {@link SpringBootAutoConfiguration#configureServletRegistrationBean} rather
     * than by hand: that method computes the mappings, init parameters and Push URL, and the
     * Atmosphere JSR-356 mapping is one of them — lose it and Push breaks, which breaks every
     * blocking dialog, with no error anywhere.
     */
    @Bean
    @ConditionalOnMissingBean(value = SpringServlet.class, parameterizedContainer = ServletRegistrationBean.class)
    public ServletRegistrationBean<SpringServlet> swingBridgeServletRegistration(
            ObjectProvider<MultipartConfigElement> multipartConfig,
            VaadinConfigurationProperties configurationProperties,
            ApplicationContext context) {
        boolean rootMapping = RootMappedCondition.isRootMapping(configurationProperties.getUrlMapping());
        return SpringBootAutoConfiguration.configureServletRegistrationBean(
                multipartConfig, configurationProperties,
                new SwingBridgeSpringServlet(context, rootMapping));
    }

    /**
     * The single mandatory init listener, which a Spring app would otherwise have to declare as a
     * bean itself — the class is {@code final}, so it is not the app's to annotate, and forgetting
     * it is what {@code MainWindowRoute} throws about on attach (R_match_swing_errors case (5),
     * M1D_bootstrap_canonical). Published here, that failure mode stops existing on this lane.
     */
    @Bean
    @ConditionalOnMissingBean
    public SwingBridgeEmulatorsBootstrap swingBridgeEmulatorsBootstrap() {
        return new SwingBridgeEmulatorsBootstrap();
    }
}
