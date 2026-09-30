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
import com.vaadin.swingbridge.surrogates.SJFormattedLongField;

import javax.swing.JFormattedTextField.AbstractFormatter;
import javax.swing.text.NumberFormatter;

/**
 * D_jformattedtextfield strategy for {@link NumberFormatter} bound to {@code Long.class}.
 * Peer is {@link SJFormattedLongField} (extends the surrogate-local
 * {@link com.vaadin.swingbridge.surrogates.LongField} per SD_sjspinner). Coerces Number subtypes
 * via {@link Number#longValue()} and rejects non-Number inputs with IAE.
 *
 * <p>Mirrors {@link IntegerStrategy} — see that file for design notes;
 * this one differs only in the Long value type.
 */
final class LongStrategy implements FormattedFieldStrategy {

    static final LongStrategy INSTANCE = new LongStrategy();

    private LongStrategy() {}

    @Override
    public Component createPeer() {
        return new SJFormattedLongField();
    }

    @Override
    public void install(JFormattedTextField field, Component peer) {
        FormattedFieldStrategy.relayBrowserEdits(field, (SJFormattedLongField) peer, v -> v);
    }

    @Override
    public void afterSetValue(JFormattedTextField field, Component peer, Object value) {
        SJFormattedLongField f = (SJFormattedLongField) peer;
        Long l;
        if (value == null) {
            l = null;
        } else if (value instanceof Number n) {
            l = n.longValue();
        } else {
            throw new IllegalArgumentException(
                    "LongStrategy: setValue expects java.lang.Number or null; got "
                            + value.getClass().getName());
        }
        f.setLongValue(l);
    }

    @Override
    public boolean accepts(AbstractFormatter formatter) {
        if (!(formatter instanceof NumberFormatter nf)) return false;
        return nf.getValueClass() == Long.class;
    }

    @Override
    public Class<?> getValueClass() {
        return Long.class;
    }
}
