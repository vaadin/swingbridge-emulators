/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: LGPL-2.1-only
 *
 * This module reproduces the API of JCalendar 1.4 (com.toedter:jcalendar),
 * which upstream licenses under the GNU Lesser General Public License, and
 * takes that licence rather than the rest of this repository's. See
 * PROVENANCE.md beside this module for why, and M1D_addon_upstream_licence in
 * migration/1-swing-to-emulators/decisions.md for the rule it follows.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version 2.1 only,
 * as published by the Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public
 * License for more details (a copy is in the LICENSE file beside this module,
 * and inside this jar at META-INF/LICENSE).
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package vaadinx.toedter.calendar;

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.github.mvysny.kaributesting.v10.mock.MockService;
import com.github.mvysny.kaributesting.v10.mock.MockVaadinServlet;
import com.github.mvysny.kaributesting.v10.mock.MockedUI;
import com.github.mvysny.blockingdialogs.uifiber.loom.VirtualThreadAwareLock;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.server.ServiceException;
import com.vaadin.flow.server.VaadinServletService;
import com.vaadin.flow.server.WrappedSession;
import com.vaadin.flow.component.datepicker.DatePicker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.BrowserTimeZone;
import com.vaadin.swingbridge.surrogates.SHelper;
import com.vaadin.swingbridge.surrogates.SJFormattedDatePicker;
import vaadinx.EHelper;

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Karibu reaches through {@link vaadinx.awt.Component#getPeer} because the peer is a real Vaadin
 * component (R_java_karibu_tests). Both warn hooks are captured because a JDateChooser straddles the
 * two layers — the emulator base is {@code :emulators}, the peer is {@code :surrogates}.
 *
 * <p>A UTC {@link BrowserTimeZone} is pre-installed: production wires {@code BrowserTimeZone.fetch()}
 * from a UI init listener, MockVaadin has no browser to round-trip with, and every {@code Date} here
 * would otherwise throw per SD_browser_timezone. UTC over a DST-bearing zone so the suite doesn't
 * break twice a year.
 */
class JDateChooserTest {

    private final List<String> warns = new ArrayList<>();
    private Consumer<String> savedEHook;
    private Consumer<String> savedSHook;

    @BeforeEach
    void setup() {
        MockVaadin.setup(MockedUI::new, new MockVaadinServlet(new Routes()) {
            // the loom runner's SessionLockCheck fails every session whose lock isn't wrapped
            @Override
            protected VaadinServletService createServletService(DeploymentConfiguration configuration) {
                final VaadinServletService service = new MockService(this, configuration, getUiFactory()) {
                    @Override
                    protected Lock getSessionLock(WrappedSession wrappedSession) {
                        return VirtualThreadAwareLock.wrap(this, wrappedSession, super.getSessionLock(wrappedSession));
                    }
                };
                try {
                    service.init();
                } catch (ServiceException e) {
                    throw new RuntimeException(e);
                }
                getRoutes().register(service.getContext());
                return service;
            }
        });
        BrowserTimeZone.setZoneId(ZoneOffset.UTC);
        savedEHook = EHelper.warnHook;
        savedSHook = SHelper.warnHook;
        EHelper.warnHook = warns::add;
        SHelper.warnHook = warns::add;
    }

    @AfterEach
    void teardown() {
        EHelper.warnHook = savedEHook;
        SHelper.warnHook = savedSHook;
        MockVaadin.tearDown();
    }

    /** Midnight UTC, so the Date↔LocalDate round-trip has nothing to truncate. */
    private static Date date(int y, int m, int d) {
        return Date.from(LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    private static SJFormattedDatePicker peerOf(JDateChooser c) {
        return (SJFormattedDatePicker) c.getPeer();
    }

    @Test
    @DisplayName("peers over a Vaadin DatePicker")
    void peersOverAVaadinDatePicker() {
        // Reads the untyped peer, not peerOf's narrowed one — a native date picker
        // instead of a hand-drawn grid of buttons is the whole point of reimplementing.
        assertInstanceOf(DatePicker.class, new JDateChooser().getPeer());
    }

    @Test
    @DisplayName("empty chooser reads back null")
    void emptyChooserReadsBackNull() {
        assertNull(new JDateChooser().getDate());
    }

    @Test
    @DisplayName("setDate round-trips through getDate")
    void setDateRoundTripsThroughGetDate() {
        JDateChooser chooser = new JDateChooser();
        chooser.setDate(date(2026, 4, 10));
        assertEquals(date(2026, 4, 10), chooser.getDate());
    }

    /** The date is the chooser's own field; this pins that the picker renders it too. */
    @Test
    @DisplayName("setDate writes through to the peer")
    void setDateWritesThroughToThePeer() {
        JDateChooser chooser = new JDateChooser();
        chooser.setDate(date(2026, 4, 10));
        assertEquals(LocalDate.of(2026, 4, 10), peerOf(chooser).getValue());
    }

    @Test
    @DisplayName("null clears the selection")
    void nullClearsTheSelection() {
        JDateChooser chooser = new JDateChooser();
        chooser.setDate(date(2026, 4, 10));
        chooser.setDate(null);
        assertNull(chooser.getDate());
        assertNull(peerOf(chooser).getValue());
    }

    @Test
    @DisplayName("programmatic setDate fires the date property with source = the chooser")
    void programmaticSetDateFiresTheDatePropertyWithSourceTheChooser() {
        JDateChooser chooser = new JDateChooser();
        List<PropertyChangeEvent> seen = new ArrayList<>();
        chooser.addPropertyChangeListener(JDateChooser.DATE_PROPERTY, seen::add);

        chooser.setDate(date(2026, 4, 10));

        assertEquals(1, seen.size());
        // Source is what migrated code casts. Without the ctor's relay this would be the
        // SJFormattedDatePicker, or nothing at all.
        assertSame(chooser, seen.get(0).getSource());
        assertNull(seen.get(0).getOldValue());
        assertEquals(date(2026, 4, 10), seen.get(0).getNewValue());
    }

    /** The peer→Swing direction (R_swing_is_truth): a browser pick must surface as the Swing-side event. */
    @Test
    @DisplayName("a value change on the peer fires the date property")
    void aValueChangeOnThePeerFiresTheDateProperty() {
        JDateChooser chooser = new JDateChooser();
        chooser.setDate(date(2026, 4, 10));
        List<PropertyChangeEvent> seen = new ArrayList<>();
        chooser.addPropertyChangeListener(JDateChooser.DATE_PROPERTY, seen::add);

        // _setValue simulates a real browser pick (isFromClient = true), which is the
        // path a migrated app's users actually take.
        LocatorJ._setValue(peerOf(chooser), LocalDate.of(2026, 4, 11));

        assertEquals(1, seen.size());
        assertSame(chooser, seen.get(0).getSource());
        assertEquals(date(2026, 4, 10), seen.get(0).getOldValue());
        assertEquals(date(2026, 4, 11), seen.get(0).getNewValue());
        assertEquals(date(2026, 4, 11), chooser.getDate(), "getDate must agree with the peer");
    }

    /** R_callswing_envelope: a DATE_PROPERTY listener that opens a modal dialog needs a UI fiber to park in. */
    @Test
    @DisplayName("a browser pick reaches the date listeners inside a UI fiber")
    void aBrowserPickReachesTheDateListenersInsideAUiFiber() {
        JDateChooser chooser = new JDateChooser();
        UI.getCurrent().add(chooser.getPeer());
        List<Boolean> inFiber = new ArrayList<>();
        chooser.addPropertyChangeListener(JDateChooser.DATE_PROPERTY,
                e -> inFiber.add(com.github.mvysny.blockingdialogs.UIFibers.isInUIFiber()));
        LocatorJ._setValue(peerOf(chooser), LocalDate.of(2026, 4, 11));
        assertEquals(List.of(true), inFiber);
    }

    /** Guards the setter's documented no-loop contract for listeners that write back. */
    @Test
    @DisplayName("re-setting the same date fires nothing")
    void reSettingTheSameDateFiresNothing() {
        JDateChooser chooser = new JDateChooser();
        chooser.setDate(date(2026, 4, 10));
        List<PropertyChangeEvent> seen = new ArrayList<>();
        chooser.addPropertyChangeListener(JDateChooser.DATE_PROPERTY, seen::add);

        chooser.setDate(date(2026, 4, 10));

        assertEquals(List.of(), seen);
    }

    /**
     * The inventory call site: construct, preselect {@code new Date()}, a wall-clock instant rather
     * than midnight. Upstream 1.4 hands back that instant, time of day included, as a fresh
     * instance (measured); the picker shows its day.
     */
    @Test
    @DisplayName("preselecting new Date reads back exactly, and the picker shows today")
    void preselectingNewDateReadsBackExactly() {
        JDateChooser chooser = new JDateChooser();
        Date now = new Date();
        chooser.setDate(now);

        assertEquals(now, chooser.getDate());
        assertNotSame(now, chooser.getDate());
        assertEquals(LocalDate.ofInstant(now.toInstant(), ZoneOffset.UTC), peerOf(chooser).getValue());
    }

    @Test
    @DisplayName("a different time on the same day fires the date property, as upstream does")
    void aDifferentTimeOnTheSameDayFires() {
        JDateChooser chooser = new JDateChooser();
        chooser.setDate(date(2026, 4, 10));
        List<PropertyChangeEvent> seen = new ArrayList<>();
        chooser.addPropertyChangeListener(JDateChooser.DATE_PROPERTY, seen::add);

        chooser.setDate(new Date(date(2026, 4, 10).getTime() + 3_600_000L));

        assertEquals(1, seen.size());
    }

    /**
     * {@code testapps/inventory}'s {@code Validator} reads the chooser inside
     * {@code SwingWorker.doInBackground()}. A peer read needed the browser zone there, which
     * a background thread has no session to find, and threw.
     */
    @Test
    @DisplayName("getDate on a background thread reads the date without a UI or session")
    void getDateOnABackgroundThread() throws InterruptedException {
        JDateChooser chooser = new JDateChooser();
        UI.getCurrent().add(chooser.getPeer());
        chooser.setDate(date(2026, 4, 10));

        AtomicReference<Object> read = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                read.set(chooser.getDate());
            } catch (Throwable e) {
                read.set(e);
            }
        });
        worker.start();
        worker.join(10_000);

        assertEquals(date(2026, 4, 10), read.get());
    }

    @Test
    @DisplayName("a date set on a background thread before the chooser is shown renders at attach")
    void aDateSetOffThreadRendersAtAttach() throws InterruptedException {
        AtomicReference<JDateChooser> built = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                JDateChooser chooser = new JDateChooser();
                chooser.setDate(date(2026, 4, 10));
                built.set(chooser);
            } catch (Throwable e) {
                failure.set(e);
            }
        });
        worker.start();
        worker.join(10_000);
        assertNull(failure.get(), () -> "the worker failed: " + failure.get());

        JDateChooser chooser = built.get();
        assertEquals(date(2026, 4, 10), chooser.getDate());
        UI.getCurrent().add(chooser.getPeer());
        assertEquals(LocalDate.of(2026, 4, 10), peerOf(chooser).getValue());
    }

    @Test
    @DisplayName("the whole surface raises no stub WARNs")
    void theWholeSurfaceRaisesNoStubWarns() {
        JDateChooser chooser = new JDateChooser();
        chooser.setDate(date(2026, 4, 10));
        chooser.getDate();
        chooser.setEnabled(false);
        chooser.setEnabled(true);
        // UIUtils and Validator both recolour a chooser to signal disabled/invalid state,
        // so setBackground is on the app's real path, not a hypothetical.
        chooser.setBackground(Color.PINK);
        chooser.setDate(null);

        assertEquals(List.of(), warns);
    }
}
