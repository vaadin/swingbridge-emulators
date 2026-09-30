/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation, with the following
 * "Classpath" exception:
 *
 *     Linking this library statically or dynamically with other modules
 *     is making a combined work based on this library.  Thus, the terms
 *     and conditions of the GNU General Public License cover the whole
 *     combination.
 *
 *     As a special exception, the copyright holders of this library give
 *     you permission to link this library with independent modules to
 *     produce an executable, regardless of the license terms of these
 *     independent modules, and to copy and distribute the resulting
 *     executable under terms of your choice, provided that you also meet,
 *     for each linked independent module, the terms and conditions of the
 *     license of that module.  An independent module is a module which is
 *     not derived from or based on this library.  If you modify this
 *     library, you may extend this exception to your version of the
 *     library, but you are not obligated to do so.  If you do not wish to
 *     do so, delete this exception statement from your version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package vaadinx.swing.app;

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.mock.MockedUI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.CustomizedSystemMessages;
import com.vaadin.flow.server.DefaultSystemMessagesProvider;
import com.vaadin.flow.server.SystemMessages;
import com.vaadin.flow.server.SystemMessagesProvider;
import com.vaadin.flow.server.SystemMessagesInfo;
import com.vaadin.flow.server.VaadinService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.MockVirtualThreadAwareServlet;

import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SwingBridgeEmulatorsBootstrap}'s "The application has ended" leg (D_auto_shutdown): an
 * expired session shows a notice rather than Flow's default reload, and an app's own
 * {@code SystemMessagesProvider} is never overridden. What the browser does with the messages
 * — no request until the user clicks — is Flow's client and was verified in Chromium.
 */
public class AppEndedMessagesTest {

    @Route("")
    public static class LandingView extends Div {
    }

    @BeforeEach
    void setup() {
        MockVaadin.setup(MockedUI::new,
                new MockVirtualThreadAwareServlet(new Routes(Set.of(LandingView.class), Set.of(), true)));
    }

    @AfterEach
    void teardown() {
        MockVaadin.tearDown();
    }

    private static SystemMessages messages(VaadinService service) {
        return service.getSystemMessages(Locale.ENGLISH, null);
    }

    private static void assertAppEnded(SystemMessages m) {
        assertTrue(m.isSessionExpiredNotificationEnabled());
        assertEquals("The application has ended", m.getSessionExpiredCaption());
        assertEquals("Click here, or press Esc, to start it again.", m.getSessionExpiredMessage());
    }

    @Test
    @DisplayName("a service the bootstrap initialised shows the notice on an expired session")
    void bootstrappedServiceShowsTheNotice() {
        // The emulators test classpath registers SwingBridgeEmulatorsBootstrap via SPI.
        assertAppEnded(messages(VaadinService.getCurrent()));
    }

    @Test
    @DisplayName("Flow's default provider is replaced")
    void defaultProviderIsReplaced() {
        VaadinService service = VaadinService.getCurrent();
        service.setSystemMessagesProvider(DefaultSystemMessagesProvider.get());
        SwingBridgeEmulatorsBootstrap.installAppEndedMessages(service);
        assertAppEnded(messages(service));
    }

    @Test
    @DisplayName("an app's own provider is left alone")
    void appsOwnProviderWins() {
        VaadinService service = VaadinService.getCurrent();
        CustomizedSystemMessages own = new CustomizedSystemMessages();
        own.setSessionExpiredCaption("Bye");
        SystemMessagesProvider provider = info -> own;
        service.setSystemMessagesProvider(provider);

        SwingBridgeEmulatorsBootstrap.installAppEndedMessages(service);

        assertSame(provider, service.getSystemMessagesProvider());
        assertSame(own, provider.getSystemMessages(new SystemMessagesInfo(Locale.ENGLISH, null, service)));
    }
}
