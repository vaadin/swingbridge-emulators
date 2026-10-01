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
import com.vaadin.swingbridge.surrogates.SJFormattedNumberField;

import javax.swing.JFormattedTextField.AbstractFormatter;
import javax.swing.text.NumberFormatter;

/**
 * D_jformattedtextfield strategy for {@link NumberFormatter} bound to {@code Number.class},
 * {@code Double.class}, {@code Float.class}, or unset value class. Peer is
 * {@link SJFormattedNumberField} (Vaadin NumberField — Double-typed).
 * Coerces Number subtypes via {@link Number#doubleValue()} and rejects
 * non-Number inputs with IAE.
 *
 * <p>Default-when-{@code getValueClass() == null} per JDK NumberFormatter
 * behavior — without a value class, the formatter returns Long for whole
 * numbers and Double for fractional. We default to Number/Double here as
 * the safer fallback (keeps JFormattedTextField rendering as a numeric
 * input even when the formatter's value class is unset). Migrators that
 * specifically want Long-typed behavior set {@code formatter.setValueClass(Long.class)}
 * to land on {@link LongStrategy}.
 *
 * <p>Mirrors {@link IntegerStrategy} — see that file for design notes;
 * this one differs only in the Double value type.
 */
final class NumberStrategy implements FormattedFieldStrategy {

    static final NumberStrategy INSTANCE = new NumberStrategy();

    private NumberStrategy() {}

    @Override
    public Component createPeer() {
        return new SJFormattedNumberField();
    }

    @Override
    public Class<? extends Component> peerType() {
        return SJFormattedNumberField.class;
    }

    @Override
    public void install(JFormattedTextField field, Component peer) {
        FormattedFieldStrategy.relayBrowserEdits(field, (SJFormattedNumberField) peer, v -> v);
    }

    @Override
    public void afterSetValue(JFormattedTextField field, Component peer, Object value) {
        SJFormattedNumberField f = (SJFormattedNumberField) peer;
        Double d;
        if (value == null) {
            d = null;
        } else if (value instanceof Number n) {
            d = n.doubleValue();
        } else {
            throw new IllegalArgumentException(
                    "NumberStrategy: setValue expects java.lang.Number or null; got "
                            + value.getClass().getName());
        }
        f.setDoubleValue(d);
    }

    @Override
    public boolean accepts(AbstractFormatter formatter) {
        if (!(formatter instanceof NumberFormatter nf)) return false;
        Class<?> vc = nf.getValueClass();
        // Accept Number / Double / Float / null (default) — anything that
        // didn't get claimed by IntegerStrategy or LongStrategy.
        return vc == null || vc == Number.class || vc == Double.class || vc == Float.class;
    }

    @Override
    public Class<?> getValueClass() {
        return Number.class;
    }
}
