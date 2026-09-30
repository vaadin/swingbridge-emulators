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
import com.vaadin.flow.component.HasValue;

import javax.swing.JFormattedTextField.AbstractFormatter;
import java.util.function.Function;

/**
 * Strategy for {@link JFormattedTextField}'s peer family — picked once at
 * ctor time from the formatter's class per
 * <a href="../../../../emulators/decisions.md#D_jformattedtextfield">D_jformattedtextfield</a>,
 * pinned for the instance's lifetime. Mirrors the {@link FrameStrategy}
 * shape (D_frame_strategy) — package-private interface, stateless singleton impls,
 * dispatch table on {@link JFormattedTextField}.
 *
 * <p>Six impls, one per Vaadin input peer: {@link DateStrategy}
 * (SJFormattedDatePicker), {@link IntegerStrategy} (SJFormattedIntegerField),
 * {@link LongStrategy} (SJFormattedLongField), {@link NumberStrategy}
 * (SJFormattedNumberField), {@link MaskStrategy} (SJFormattedTextField masked),
 * and {@link DefaultFormattedStrategy} (SJTextField) as the catch-all for
 * DefaultFormatter / no formatter / any family without a dedicated strategy.
 * {@code JFormattedTextField.pickStrategyFor*} maps a formatter or value to one
 * of these at ctor time.
 *
 * <p>Per-strategy state lives on the {@link JFormattedTextField} instance
 * (the {@code value} field shadow, the {@code formatter}, the
 * {@code focusLostBehavior} int); strategies are stateless dispatch
 * surfaces.
 */
interface FormattedFieldStrategy {

    /**
     * Vaadin peer to host the JFormattedTextField instance. Called once
     * from the private {@code (FormattedFieldStrategy)} ctor; result is
     * passed up the {@code super(peer)} chain through JTextField's
     * widened protected ctor (D_jtextfield_ctor_widening — non-TextField/PasswordField peers
     * skip the Enter-as-submit wiring).
     */
    Component createPeer();

    /**
     * Wire peer-side commit listeners — typically {@link #relayBrowserEdits}, and any
     * peer-specific focus / Enter listeners. Called once at the end of the private ctor,
     * after {@code super(peer)} has assigned the {@code peer} field.
     */
    void install(JFormattedTextField field, Component peer);

    /**
     * Mirrors a browser edit of {@code peer} into {@link JFormattedTextField#setValue}, inside
     * a UI fiber so the field's {@code "value"} listeners can open a modal dialog
     * (R_callswing_envelope).
     *
     * <pre>{@code
     * relayBrowserEdits(field, (SJFormattedDatePicker) peer, BrowserDateUtils::toDate);
     * }</pre>
     *
     * <p>Subscribes to the peer's {@code HasValue} event rather than the surrogate's
     * {@code "value"} property change: the surrogate fires that inside its inline
     * {@code SHelper.callSwing}, which runs no fiber. Only a client-side change is
     * relayed — a server-side one is the field's own {@link #afterSetValue} push,
     * whose value the field already holds.
     *
     * @param toSwing converts the peer's value to the one the field stores
     */
    static <V> void relayBrowserEdits(JFormattedTextField field, HasValue<?, V> peer,
                                      Function<? super V, Object> toSwing) {
        peer.addValueChangeListener(e -> {
            if (!e.isFromClient()) return;
            vaadinx.EHelper.callSwing(() -> field.setValue(toSwing.apply(e.getValue())));
        });
    }

    /**
     * Push the JFormattedTextField's value into the peer + Document. Called
     * from {@link JFormattedTextField#setValue(Object)} after the field
     * shadow updates and the emulator-side
     * {@link java.beans.PropertyChangeEvent}{@code ("value")} fires.
     */
    void afterSetValue(JFormattedTextField field, Component peer, Object value);

    /**
     * Family check for the swap rule per
     * <a href="../../../../emulators/decisions.md#D_formatter_swap_rules">D_formatter_swap_rules</a>:
     * a {@link JFormattedTextField#setFormatter setFormatter} call that
     * passes a formatter for which the active strategy returns {@code true}
     * here re-runs formatter logic in-place; a formatter for which it
     * returns {@code false} WARNs and keeps the original strategy.
     */
    boolean accepts(AbstractFormatter formatter);

    /**
     * The strategy's nominal value class — Date for DateStrategy,
     * Integer/Long/Number for the numeric strategies, String for
     * DefaultStrategy. Used for round-trip {@link JFormattedTextField}
     * introspection (matches the formatter's getValueClass when set).
     */
    Class<?> getValueClass();
}
