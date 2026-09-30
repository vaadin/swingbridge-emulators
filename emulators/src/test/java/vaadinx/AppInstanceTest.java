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

package vaadinx;

import com.github.mvysny.kaributesting.v10.MockBrowser;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.mock.MockedUI;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.PreserveOnRefresh;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AppInstance} — the app-instance lifetime M1D_former_singletons's {@code FormerSingletons} holder
 * lives in. The four properties that matter to a migrated app: one instance per
 * running app, a distinct one per browser tab, survival across F5, and
 * reachability from the request-less teardown threads where {@code WINDOW_CLOSING}
 * cleanup runs. Plus the three throws, which are the whole reason it is not a
 * bare {@code TabScope.getValues()} call.
 */
class AppInstanceTest {

    /** Stand-in for a migrated app's {@code FormerSingletons}. */
    public static class Holder {
        String label;
    }

    /** A second holder type, to pin that the store keys on the type. */
    public static class OtherHolder {
    }

    /** Landing route so {@code MockVaadin.setup}'s default navigation lands somewhere. */
    @Route("")
    @PreserveOnRefresh
    public static class AppInstanceLanding extends Div {
    }

    @BeforeEach
    void setup() {
        MockVaadin.setup(MockedUI::new,
                new MockVirtualThreadAwareServlet(new Routes(Set.of(AppInstanceLanding.class), Set.of(), true)));
        AppTab.markAppUI(UI.getCurrent());
    }

    @AfterEach
    void teardown() {
        MockVaadin.tearDown();
    }

    @Test
    @DisplayName("the factory runs once, and later calls see the same instance")
    void theFactoryRunsOnceAndLaterCallsSeeTheSameInstance() {
        Counter built = new Counter();
        Holder first = AppInstance.get(Holder.class, () -> {
            built.inc();
            return new Holder();
        });
        first.label = "written once";

        Holder second = AppInstance.get(Holder.class, () -> {
            built.inc();
            return new Holder();
        });

        assertSame(first, second);
        assertEquals("written once", second.label);
        built.assertEquals(1);
    }

    @Test
    @DisplayName("distinct types get distinct instances")
    void distinctTypesGetDistinctInstances() {
        Holder holder = AppInstance.get(Holder.class, Holder::new);
        OtherHolder other = AppInstance.get(OtherHolder.class, OtherHolder::new);
        assertSame(holder, AppInstance.get(Holder.class, Holder::new));
        assertSame(other, AppInstance.get(OtherHolder.class, OtherHolder::new));
    }

    @Test
    @DisplayName("a second browser tab gets its own instance")
    void aSecondBrowserTabGetsItsOwnInstance() {
        Holder inTabA = AppInstance.get(Holder.class, Holder::new);
        inTabA.label = "tab A";

        MockBrowser.newTab("tab-B", "");
        Holder inTabB = AppInstance.get(Holder.class, Holder::new);

        assertNotSame(inTabA, inTabB, "app-instance state must not leak between tabs");
        assertNull(inTabB.label);
    }

    @Test
    @DisplayName("an F5 reload keeps the same instance")
    void anF5ReloadKeepsTheSameInstance() {
        Holder before = AppInstance.get(Holder.class, Holder::new);
        before.label = "survives F5";
        UI uiBefore = UI.getCurrent();

        UI.getCurrent().getPage().reload();

        assertNotSame(uiBefore, UI.getCurrent(), "reload must create a new UI");
        Holder after = AppInstance.get(Holder.class, Holder::new);
        assertSame(before, after, "the tab scope is window.name-keyed, so F5 keeps the app instance");
        assertEquals("survives F5", after.label);
    }

    @Test
    @DisplayName("resolves with a session but no current UI — the teardown window")
    void resolvesWithASessionButNoCurrentUiTheTeardownWindow() {
        Holder stored = AppInstance.get(Holder.class, Holder::new);
        stored.label = "written while the app ran";

        // What a WINDOW_CLOSING handler sees: the tab-scope reaper / session-destroy
        // thread has the session but no UI (D_shutdown_lifecycle).
        UI.setCurrent(null);
        assertNotNull(VaadinSession.getCurrent(), "precondition: the session is still current");

        Holder atTeardown = AppInstance.get(Holder.class, Holder::new);

        assertSame(stored, atTeardown);
        assertEquals("written while the app ran", atTeardown.label);
    }

    @Test
    @DisplayName("throws off any Vaadin context, naming the background thread")
    void throwsOffAnyVaadinContextNamingTheBackgroundThread() throws Exception {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Throwable thrown = pool.submit(() -> {
                try {
                    AppInstance.get(Holder.class, Holder::new);
                    return (Throwable) null;
                } catch (Throwable t) {
                    return t;
                }
            }).get(5, TimeUnit.SECONDS);
            IllegalStateException ise = assertInstanceOf(IllegalStateException.class, thrown,
                    "expected IllegalStateException, got " + thrown);
            assertTrue(ise.getMessage().contains("doInBackground"),
                    "the message must point at the cause and the fix; got: " + ise.getMessage());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("throws before the browser has reported in, naming SwingBridgeEmulatorsBootstrap")
    void throwsBeforeTheBrowserHasReportedInNamingSwingBridgeEmulatorsBootstrap() {
        // The static-initializer / unwired-app shape: a UI is current, but no tab
        // scope can be resolved for it.
        UI.getCurrent().getInternals().setExtendedClientDetails(null);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> AppInstance.get(Holder.class, Holder::new));
        assertTrue(e.getMessage().contains("SwingBridgeEmulatorsBootstrap"), "got: " + e.getMessage());
    }

    @Test
    @DisplayName("a factory returning null is rejected rather than stored")
    void aFactoryReturningNullIsRejectedRatherThanStored() {
        assertThrows(NullPointerException.class, () -> AppInstance.get(Holder.class, () -> null));
    }
}
