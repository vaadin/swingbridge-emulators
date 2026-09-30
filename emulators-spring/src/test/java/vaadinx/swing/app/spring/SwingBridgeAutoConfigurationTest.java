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

import com.vaadin.flow.spring.SpringBootAutoConfiguration;
import com.vaadin.flow.spring.SpringServlet;
import com.vaadin.flow.spring.VaadinConfigurationProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vaadinx.swing.app.SwingBridgeEmulatorsBootstrap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The auto-configuration's contract, asserted without starting a servlet container. See
 * D_library_servlet and R_no_spi_selfregister's framework-dedicated-module exception, whose third
 * limb ("conditional and overridable") is {@link #appsOwnServletRegistrationWins()}.
 *
 * <p><b>Vaadin's own {@link SpringBootAutoConfiguration} is present in every test here, and that
 * is the point</b> — ours exists to displace it, so testing it alone would assert nothing about
 * the arrangement a migrated app actually runs. Two consequences to know before editing: the
 * runner must be a {@link WebApplicationContextRunner}, because Vaadin's auto-configuration
 * autowires a {@code WebApplicationContext}; and nothing here declares
 * {@link VaadinConfigurationProperties}, because Vaadin's contributes it and a second one makes
 * the injection ambiguous rather than redundant.
 */
public class SwingBridgeAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    PropertyPlaceholderAutoConfiguration.class,
                    SwingBridgeAutoConfiguration.class,
                    SpringBootAutoConfiguration.class));

    /**
     * The whole point of {@code @AutoConfiguration(before = SpringBootAutoConfiguration.class)}:
     * ours must register first so Vaadin's {@code @ConditionalOnMissingBean} backs off. Get the
     * order wrong and the app silently gets Vaadin's plain {@code SpringServlet}, whose session
     * lock is unwrapped, and the failure is a {@code StackOverflowError} at the first modal dialog.
     */
    @Test
    public void oursReplacesVaadinsOwnRegistration() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(ServletRegistrationBean.class)).hasSize(1);
            assertThat(context.getBean(ServletRegistrationBean.class).getServlet())
                    .isInstanceOf(SwingBridgeSpringServlet.class);
        });
    }

    /**
     * The bean a Spring app would otherwise declare itself — the class is {@code final}, so it is
     * not the app's to annotate, and forgetting it is what {@code MainWindowRoute} throws about on
     * attach (R_match_swing_errors case (5)). Published here, that failure mode stops existing.
     */
    @Test
    public void registersTheBootstrapListenerSoTheAppNeedNot() {
        runner.run(context -> assertThat(context).hasSingleBean(SwingBridgeEmulatorsBootstrap.class));
    }

    /**
     * R_no_spi_selfregister's third limb: the registration is overridable, so "the app does the
     * wiring" stays true in substance. An app declaring its own registration must win outright —
     * not end up with two servlets mapped at the same path.
     */
    @Test
    public void appsOwnServletRegistrationWins() {
        runner.withUserConfiguration(AppOwnServlet.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(ServletRegistrationBean.class)).hasSize(1);
            assertThat(context.getBean(ServletRegistrationBean.class).getServlet())
                    .isNotInstanceOf(SwingBridgeSpringServlet.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class AppOwnServlet {
        @Bean
        ServletRegistrationBean<SpringServlet> vaadinServletRegistration(
                VaadinConfigurationProperties properties, ApplicationContext context) {
            return SpringBootAutoConfiguration.configureServletRegistrationBean(
                    context.getBeanProvider(jakarta.servlet.MultipartConfigElement.class),
                    properties, new SpringServlet(context, true));
        }
    }

    /**
     * R_match_swing_errors case (10) moved to context refresh. The switch is a Spring property, so
     * this is the only place in SB-Emulators that can catch it before the first peer→Swing
     * callback — where {@code callSwing}'s re-entrancy fast path would otherwise absorb it with no
     * WARN and no stack trace.
     */
    @Test
    public void refusesVirtualRequestThreads() {
        runner.withPropertyValues("spring.threads.virtual.enabled=true").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .rootCause()
                    .hasMessageContaining("spring.threads.virtual.enabled=true")
                    .hasMessageContaining("must be platform threads");
        });
    }

    /** The default, and the only configuration SB-Emulators supports, must not be refused. */
    @Test
    public void acceptsPlatformRequestThreads() {
        runner.withPropertyValues("spring.threads.virtual.enabled=false")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
