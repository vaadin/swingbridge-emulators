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

// Clean reimplementation of com.toedter.calendar.JDateChooser (JCalendar 1.4) —
// no upstream source is copied; see this module's PROVENANCE.md.
//
// Why reimplemented rather than import-swapped, which would have worked:
// upstream builds its calendar from ~49 JButtons in a JDayChooser grid, so the
// swap renders a hand-drawn calendar per date field on a platform that has a
// native date picker. Reproducing the API over SJFormattedDatePicker is smaller
// and drops the whole supporting-class closure.
//
// The selected Date is this class's own field, so getDate() is a field read that works on
// any thread; the peer renders it. SJFormattedDatePicker owns the Date<->LocalDate coercion
// in the browser's zone (SD_browser_timezone).

import com.vaadin.flow.server.VaadinSession;
import com.vaadin.swingbridge.surrogates.SJFormattedDatePicker;
import com.vaadin.swingbridge.surrogates.util.BrowserDateUtils;
import vaadinx.EHelper;

import java.util.Date;

/**
 * Date-picker field with the API of JCalendar 1.4's {@code com.toedter.calendar.JDateChooser},
 * rendering as a Vaadin date picker:
 *
 * <pre>{@code
 * JDateChooser sentDate = new JDateChooser();
 * sentDate.setDate(new Date());              // preselect today
 * cartPanel.add(sentDate, "14, 4, fill, bottom");
 *
 * Date when = sentDate.getDate();            // null once the user clears it
 * sentDate.addPropertyChangeListener(JDateChooser.DATE_PROPERTY, e -> revalidate());
 * }</pre>
 *
 * <p>Only that surface is reproduced. The {@code IDateEditor} / {@code JCalendar} / date-format
 * constructors, {@code get}/{@code setCalendar}, {@code get}/{@code setDateFormatString},
 * {@code getJCalendar}, {@code getCalendarButton} and {@code getDateEditor} ship no method at
 * all, so reaching for one is a compile error naming it rather than a silent no-op.
 *
 * <p>Extends {@link vaadinx.swing.JComponent}, where upstream extends {@code JPanel}:
 * {@link vaadinx.swing.JPanel} is peer-locked per R_leaf_peer_lockdown, leaving {@code JComponent} as the
 * extension point an add-on module can reach. Treating a chooser as a {@code JComponent} —
 * {@code setEnabled}, {@code setBackground}, adding it to a container, {@code instanceof} — is
 * unaffected; casting one to {@code JPanel} is not supported.
 *
 * <p>{@link #getDate()} returns exactly the {@code Date} last set, time of day included, as
 * upstream's does (measured on JCalendar 1.4). A date the user picks in the browser reads back
 * as midnight of that day in the browser's zone.
 *
 * <p>Rendering a {@code Date} in the date picker converts it to a day in the
 * <em>browser's</em> zone, so {@code BrowserTimeZone.fetch()} must have run from a UI
 * init listener ({@code SwingBridgeEmulatorsBootstrap}, which every migrated app
 * registers per R_no_spi_selfregister). Without it that conversion throws instead of
 * silently rendering dates an hour off — SD_browser_timezone. It runs when the date
 * reaches the picker: in {@link #setDate}, or, for a chooser given a date off the UI
 * thread before it was ever shown, when it is first attached.
 */
public class JDateChooser extends vaadinx.swing.JComponent {

    /** Bound property fired when the selected date changes, from either side. */
    public static final String DATE_PROPERTY = "date";

    /** The selected date, or {@code null}; a private copy, so the caller's instance can change freely. */
    private Date date;

    public JDateChooser() {
        this(new SJFormattedDatePicker());
    }

    /**
     * Peer lock-down in the spirit of R_leaf_peer_lockdown — private and typed-narrow, so a user-code
     * subclass can't reach it to swap the peer type.
     */
    private JDateChooser(SJFormattedDatePicker peer) {
        super(peer);
        // The peer's own event, not the surrogate's "value" property change: that one fires
        // inside the surrogate's inline SHelper.callSwing, where a listener opening a modal
        // dialog has no UI fiber to park. A server-side change is pushDate's echo.
        peer.addValueChangeListener(e -> {
            if (!e.isFromClient()) return;
            EHelper.callSwing(() -> {
                Date old = date;
                date = BrowserDateUtils.toDate(e.getValue());
                firePropertyChange(DATE_PROPERTY, old, date);
            });
        });
        // Flushes a date set before the peer could render it; see pushDate.
        peer.addAttachListener(e -> pushDate());
    }

    /** Narrow the peer to its surrogate type. Peer is always an SJFormattedDatePicker. */
    private SJFormattedDatePicker picker() {
        return (SJFormattedDatePicker) getPeer();
    }

    /** @return a new instance equal to the selected date, or {@code null} when the field is empty */
    public Date getDate() {
        return date == null ? null : new Date(date.getTime());
    }

    /**
     * Selects {@code date}, or clears the selection when it is {@code null}.
     *
     * @param date copied; fires {@link #DATE_PROPERTY} only when it is not
     *             {@linkplain Date#equals equal} to the current one, so a listener that writes
     *             back doesn't loop
     */
    public void setDate(Date date) {
        Date old = this.date;
        this.date = date == null ? null : new Date(date.getTime());
        pushDate();
        firePropertyChange(DATE_PROPERTY, old, this.date);
    }

    /**
     * Renders {@link #date} in the peer. Skipped when no session is current, which inside
     * {@code withPeer} means a peer never attached, written from a background thread: the
     * browser zone lives on the session, so there is none to convert with, and the attach
     * listener pushes the date instead.
     */
    private void pushDate() {
        Date d = date;
        withPeer(p -> {
            if (VaadinSession.getCurrent() == null) return;
            picker().setDateValue(d);
        });
    }
}
