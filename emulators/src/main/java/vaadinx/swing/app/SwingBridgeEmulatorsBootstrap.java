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

import com.github.mvysny.vaadin.tabscope.TabScope;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.CustomizedSystemMessages;
import com.vaadin.flow.server.DefaultSystemMessagesProvider;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinServiceInitListener;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.swingbridge.surrogates.BrowserTimeZone;
import com.vaadin.swingbridge.surrogates.FocusTracker;
import vaadinx.AppTab;

/**
 * The single init listener a migrated app wires — one line in its own SPI file
 * (or a Spring {@code @Bean}: the class is {@code final} and library-owned, so
 * it is not the app's to annotate) — priming everything the SB-Emulators runtime needs
 * per session:
 *
 * <pre>{@code
 * // META-INF/services/com.vaadin.flow.server.VaadinServiceInitListener
 * vaadinx.swing.app.SwingBridgeEmulatorsBootstrap
 * }</pre>
 *
 * Five legs:
 * <ul>
 *   <li><b>Browser time zone</b> — {@code BrowserTimeZone.fetch()} on every UI
 *       init, so {@code Date}↔{@code LocalDate} conversion uses the user's zone
 *       (SD_browser_timezone — without it, date-bearing inputs throw). Wired unconditionally,
 *       browser or not.</li>
 *   <li><b>Focus tracking</b> — {@code FocusTracker.install()} on every UI
 *       init, planting the one {@code focusin} listener that
 *       {@code KeyboardFocusManager.getFocusOwner()} and focus traversal read
 *       (D_focus_managers). Wired unconditionally: the listener registration is inert under
 *       Karibu, which never dispatches DOM events.</li>
 *   <li><b>Emulator theme</b> — {@code context://emul/emulator-theme.css} on
 *       every UI's page, the default-fill rules D_theme_is_lookandfeel's class
 *       mechanism resolves against.</li>
 *   <li><b>Tab-close detection</b> — the vaadin-tab-scope handshake
 *       ({@code TabScope.setup} + a destroy listener → {@link
 *       AppTab#onTabClosed}), so a real app-tab close ends the session (D_shutdown_lifecycle).
 *       Wired unconditionally, browser or not: Karibu fakes {@code
 *       ExtendedClientDetails}, so the handshake completes browserlessly and a
 *       tab scope exists under {@code MockService} too — which M1D_former_singletons's
 *       tab-scoped {@code FormerSingletons} holder relies on.</li>
 *   <li><b>"The application has ended"</b> — {@code SystemMessages} whose session-expired
 *       notice replaces Flow's default reload, which booted a fresh app instance the moment
 *       the app ended (D_auto_shutdown). Installed only over Flow's default provider, so an
 *       app's own {@code SystemMessagesProvider} wins.</li>
 * </ul>
 *
 * <p>It also fails the deployment fast on a JDK older than 24, via {@link
 * #assertSupportedRuntime(Runtime.Version)} — servlet init is the last legible moment before the
 * first modal dialog deadlocks (R_match_swing_errors case (6)).
 *
 * <p><b>The app wires it, never the library</b> (R_no_spi_selfregister): an SPI-registered
 * {@code VaadinServiceInitListener} shipped by {@code :emulators} would conflict
 * with Spring's bean discovery, so the class ships here and the app registers it
 * (Sampler's SPI file; a Spring app as a {@code @Bean}). It is a hard
 * requirement — an app that skips it gets no browser-zone dates (SD_browser_timezone throw),
 * no focus owner, and no tab-close shutdown.
 *
 * <p><b>Register this class; do not copy its body into a listener of your own.</b> Legs are
 * added over time — the theme stylesheet arrived after the migration guide's copy was written,
 * which is how that guide came to wire one leg of four — and a copy misses the next one
 * silently. {@link MainWindowRoute} throws on attach when this class's UI-init leg never ran
 * (M1D_bootstrap_canonical).
 */
public final class SwingBridgeEmulatorsBootstrap implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        // Fail-fast on a JDK 21 deployment (R_match_swing_errors case (6)). Here rather than
        // left to the loom runner, which loads lazily on the first callSwing — far too late
        // to read as a configuration error — and which accepts 21-23 behind a system property
        // SB-Emulators does not honour.
        assertSupportedRuntime(Runtime.version());

        installAppEndedMessages(event.getSource());

        // Per-UI legs, both needed browser or not — so they are wired even under
        // the browserless mock: the browser time zone every date-bearing input
        // reads (SD_browser_timezone), and the focus pointer's focusin bridge (SD_focus_tracker / D_focus_managers).
        event.getSource().addUIInitListener(uiInit -> {
            // The isWired marker MainWindowRoute checks. UI init listeners run
            // synchronously at UI creation, before any route attaches, so the
            // marker is already set by the time the route can ask. The value is
            // this listener because ComponentUtil's typed slot takes the key
            // class's own type; only its presence is read.
            ComponentUtil.setData(uiInit.getUI(), SwingBridgeEmulatorsBootstrap.class, this);
            BrowserTimeZone.fetch();
            FocusTracker.install();
            // The default-fill rules D_theme_is_lookandfeel's class mechanism
            // resolves against. Added here rather than via @StyleSheet because
            // the class is applied by vaadinx.awt.Component, which is not itself
            // a Vaadin component and so has nothing for Vaadin to scan — and
            // hanging the annotation on one surrogate would leave an app that
            // never instantiates that one component unstyled.
            uiInit.getUI().getPage().addStyleSheet("context://emul/emulator-theme.css");
        });

        // Tab-close leg. Wired browser or not: Karibu fakes ExtendedClientDetails,
        // so TabScope.init's retrieveExtendedClientDetails callback fires and the
        // scope is created under MockService too. This leg is not only about
        // tab-close — M1D_former_singletons's FormerSingletons holder lives in the tab scope, so a
        // test running without one could not resolve it.
        TabScope.setup(ts -> {
            // Runs once per browser tab. Capture the session now (a request is in
            // flight, so it's resolvable) for the later destroy hook.
            final VaadinSession session = VaadinSession.getCurrent();
            ts.addDestroyListener(scope -> AppTab.onTabClosed(session, scope));
        });
    }

    /**
     * Replaces Flow's default {@code SystemMessagesProvider} with one whose expired-session
     * notice reads "The application has ended"; leaves any other provider alone.
     *
     * <p>Flow's default answers an expired session with a silent reload, and for a
     * migrated app a reload is a relaunch: {@code File → Exit} came straight back as a new
     * app instance (D_auto_shutdown). The notice waits for the user instead — the client
     * has already stopped heartbeat and push, so nothing reaches the server, and no new
     * session is created, until they click it or press Esc.
     */
    static void installAppEndedMessages(VaadinService service) {
        if (service.getSystemMessagesProvider() != DefaultSystemMessagesProvider.get()) {
            return;
        }
        final CustomizedSystemMessages messages = new CustomizedSystemMessages();
        messages.setSessionExpiredNotificationEnabled(true);
        messages.setSessionExpiredCaption("The application has ended");
        messages.setSessionExpiredMessage("Click here, or press Esc, to start it again.");
        service.setSystemMessagesProvider(info -> messages);
    }

    /**
     * Whether this class's UI-init leg ran for {@code ui} — i.e. the app wired it.
     *
     * @return {@code false} when the app registered no {@code SwingBridgeEmulatorsBootstrap}, or registered it
     *         after {@code ui} was created
     */
    public static boolean isWired(UI ui) {
        return ComponentUtil.getData(ui, SwingBridgeEmulatorsBootstrap.class) != null;
    }

    /**
     * Refuses a JVM older than 24, where every modal {@code callSwing} would deadlock instead of
     * parking: pre-<a href="https://openjdk.org/jeps/491">JEP 491</a> a virtual thread parking
     * inside {@code synchronized} pins its carrier — the Vaadin request thread holding the session
     * lock. The enforcer gates the building JVM; this gates the deploying one.
     *
     * @param version parameterized so a JDK 24+ suite can test the JDK 21 branch.
     * @throws IllegalStateException if {@code version}'s feature version is below 24
     */
    static void assertSupportedRuntime(Runtime.Version version) {
        if (version.feature() < 24) {
            throw new IllegalStateException("SwingBridge Emulators requires a JDK 24+ runtime, but this JVM is "
                    + version + ". Before JEP 491 a virtual thread that parks inside a synchronized block pins its "
                    + "carrier — here the Vaadin request thread holding the session lock — so the first modal dialog "
                    + "deadlocks with no stack trace. Run the app on JDK 24+.");
        }
    }
}
