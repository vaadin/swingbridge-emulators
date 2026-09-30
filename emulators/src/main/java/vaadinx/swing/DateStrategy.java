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

package vaadinx.swing;

import com.vaadin.flow.component.Component;
import com.vaadin.swingbridge.surrogates.SJFormattedDatePicker;
import com.vaadin.swingbridge.surrogates.util.BrowserDateUtils;

import javax.swing.JFormattedTextField.AbstractFormatter;
import javax.swing.text.DateFormatter;
import javax.swing.text.InternationalFormatter;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * D_jformattedtextfield strategy for {@link DateFormatter} (or {@link InternationalFormatter}
 * wrapping a {@link DateFormat}). Peer is {@link SJFormattedDatePicker} —
 * Vaadin DatePicker with calendar popup + locale-driven typed-input.
 *
 * <p>Round-trip semantics:
 * <ul>
 *   <li><b>Programmatic {@code field.setValue(date)}</b>: the field shadow
 *       updates, fires PCE("value") on the emulator, then {@link #afterSetValue}
 *       coerces the Object to JDK {@link Date} and pushes through to
 *       {@link SJFormattedDatePicker#setDateValue}. That server-side write
 *       is not relayed back.</li>
 *   <li><b>Browser-driven date pick</b>: the {@link #install} bridge
 *       converts the picked day to a {@code Date} and calls
 *       {@code field.setValue(newDate)} in a UI fiber, which takes the
 *       full setValue path (PCE on emulator, afterSetValue → peer write
 *       short-circuits due to peer already at newDate).</li>
 * </ul>
 *
 * <p>Calendar values are coerced to {@code cal.getTime()}; non-Date /
 * non-Calendar inputs throw IAE per R_match_swing_errors (matches JDK behavior when the
 * formatter's value class doesn't match).
 */
final class DateStrategy implements FormattedFieldStrategy {

    static final DateStrategy INSTANCE = new DateStrategy();

    private DateStrategy() {}

    @Override
    public Component createPeer() {
        return new SJFormattedDatePicker();
    }

    @Override
    public void install(JFormattedTextField field, Component peer) {
        FormattedFieldStrategy.relayBrowserEdits(field, (SJFormattedDatePicker) peer, BrowserDateUtils::toDate);
    }

    @Override
    public void afterSetValue(JFormattedTextField field, Component peer, Object value) {
        SJFormattedDatePicker dp = (SJFormattedDatePicker) peer;
        Date d;
        if (value == null) {
            d = null;
        } else if (value instanceof Date dv) {
            d = dv;
        } else if (value instanceof Calendar cal) {
            d = cal.getTime();
        } else {
            throw new IllegalArgumentException(
                    "DateStrategy: setValue expects java.util.Date, java.util.Calendar, or null; got "
                            + value.getClass().getName());
        }
        dp.setDateValue(d);
    }

    @Override
    public boolean accepts(AbstractFormatter formatter) {
        if (formatter == null) return false;
        if (formatter instanceof DateFormatter) return true;
        if (formatter instanceof InternationalFormatter intl) {
            return intl.getFormat() instanceof DateFormat;
        }
        return false;
    }

    @Override
    public Class<?> getValueClass() {
        return Date.class;
    }
}
