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

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.mock.MockedUI;
import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import com.vaadin.swingbridge.surrogates.BrowserTimeZone;

import java.time.ZoneOffset;
import java.util.List;

/**
 * Sets up Karibu-Testing around each test. Subclasses get a live Vaadin UI context.
 *
 * <p>Shared test infrastructure, in Java like every test in the repo (R_java_karibu_tests).
 *
 * <p>Uses {@link MockVirtualThreadAwareServlet} (not the default
 * {@link com.github.mvysny.kaributesting.v10.mock.MockVaadinServlet})
 * because {@link EHelper#callSwing} runs every peer→Swing event as a UI fiber on
 * vaadin-blocking-dialogs' loom runner, which refuses a session whose lock is not the
 * {@code VirtualThreadAwareLock} wrapper (its {@code SessionLockCheck}, at session init) —
 * the default mock session's plain {@code ReentrantLock} is held by the test thread, not by
 * the fiber's virtual thread, so {@code hasLock()} would read {@code false} inside it and
 * Vaadin's element-update path would reject peer writes (D_vt_aware_session_lock).
 *
 * <p>Pre-installs a UTC {@link BrowserTimeZone} so Date-bearing components
 * ({@code JSpinner(SpinnerDateModel)}, {@code JFormattedTextField} + {@code DateFormatter})
 * don't throw — production wiring is {@code BrowserTimeZone.fetch()} from a UI
 * init listener, but MockVaadin has no real browser to round-trip with.
 * UTC over a DST-bearing zone so tests don't break twice a year (SD_browser_timezone).
 */
public abstract class AbstractKaribuTest {

    @BeforeEach
    public void setupKaribu() {
        MockVaadin.setup(MockedUI::new, new MockVirtualThreadAwareServlet(new Routes()));
        BrowserTimeZone.setZoneId(ZoneOffset.UTC);
        // Mark the mock UI as the app tab so background-delivered callbacks
        // (Timer fires, SwingWorker done()/process(), post-park continuations)
        // resolve a delivery UI via EHelper.singleLiveUI — which is now pure
        // (AppTab-or-null, no getUIs() guess). Production wires this in
        // MainWindowRoute.onAttach; the bare mock route has no such attach, so
        // the base test stands in for it. Tests that navigate to their own
        // MainWindowRoute or call markAppUI themselves (F5 / tab-close tests)
        // just re-bind on top of this. See D_session_scoped_pools/D_active_ui_pointer (emulators/decisions.md).
        AppTab.markAppUI(UI.getCurrent());
    }

    @AfterEach
    public void teardownKaribu() {
        MockVaadin.tearDown();
    }

    /** @see #assertNoWarns(List, String) */
    protected void assertNoWarns(List<String> warns) {
        assertNoWarns(warns, null);
    }

    /**
     * Asserts no stub WARNs were captured into {@code warns}. Compares against the
     * empty list (not {@code assertTrue(warns.isEmpty())}) so a failure dumps the
     * offending WARN strings instead of a bare "expected true, was false". Takes the
     * list explicitly because the emulator tests capture WARNs through varied
     * mechanisms (per-field, method-local save/restore, EHelper-only vs
     * EHelper+SHelper) that are intentionally left in place.
     */
    protected void assertNoWarns(List<String> warns, String message) {
        Assertions.assertEquals(List.of(), warns, message);
    }
}
