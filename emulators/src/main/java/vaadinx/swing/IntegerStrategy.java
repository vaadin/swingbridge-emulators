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
import com.vaadin.swingbridge.surrogates.SJFormattedIntegerField;

import javax.swing.JFormattedTextField.AbstractFormatter;
import javax.swing.text.NumberFormatter;

/**
 * D_jformattedtextfield strategy for {@link NumberFormatter} bound to {@code Integer.class}.
 * Peer is {@link SJFormattedIntegerField} (Vaadin IntegerField). Coerces
 * Number subtypes via {@link Number#intValue()} and rejects non-Number
 * inputs with IAE per R_match_swing_errors.
 *
 * <p>Round-trip semantics mirror {@link DateStrategy}: programmatic
 * {@code field.setValue(int)} drives the field shadow + PCE on the
 * emulator, then {@link #afterSetValue} pushes the Integer to the peer
 * via {@link SJFormattedIntegerField#setIntValue(Integer)}; the
 * {@link #install} bridge mirrors browser-driven value changes back into
 * {@link JFormattedTextField#setValue}.
 */
final class IntegerStrategy implements FormattedFieldStrategy {

    static final IntegerStrategy INSTANCE = new IntegerStrategy();

    private IntegerStrategy() {}

    @Override
    public Component createPeer() {
        return new SJFormattedIntegerField();
    }

    @Override
    public void install(JFormattedTextField field, Component peer) {
        FormattedFieldStrategy.relayBrowserEdits(field, (SJFormattedIntegerField) peer, v -> v);
    }

    @Override
    public void afterSetValue(JFormattedTextField field, Component peer, Object value) {
        SJFormattedIntegerField f = (SJFormattedIntegerField) peer;
        Integer i;
        if (value == null) {
            i = null;
        } else if (value instanceof Number n) {
            i = n.intValue();
        } else {
            throw new IllegalArgumentException(
                    "IntegerStrategy: setValue expects java.lang.Number or null; got "
                            + value.getClass().getName());
        }
        f.setIntValue(i);
    }

    @Override
    public boolean accepts(AbstractFormatter formatter) {
        if (!(formatter instanceof NumberFormatter nf)) return false;
        Class<?> vc = nf.getValueClass();
        return vc == Integer.class;
    }

    @Override
    public Class<?> getValueClass() {
        return Integer.class;
    }
}
