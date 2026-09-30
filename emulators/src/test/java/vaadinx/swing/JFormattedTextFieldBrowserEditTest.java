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

import com.github.mvysny.blockingdialogs.UIFibers;
import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.vaadin.flow.component.HasValue;
import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;

import javax.swing.text.DateFormatter;
import javax.swing.text.NumberFormatter;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A browser edit of each typed {@link JFormattedTextField} peer reaches the field's
 * {@code "value"} listeners inside a UI fiber, so one of them can open a modal dialog
 * (R_callswing_envelope).
 */
class JFormattedTextFieldBrowserEditTest extends AbstractKaribuTest {

    private static JFormattedTextField numberField(Class<?> valueClass) {
        NumberFormatter nf = new NumberFormatter(NumberFormat.getInstance());
        nf.setValueClass(valueClass);
        return new JFormattedTextField(nf);
    }

    /** Picks {@code browserValue} in the peer and returns what the field's value listeners saw. */
    @SuppressWarnings("unchecked")
    private static <V> List<Object> browserEdit(JFormattedTextField f, V browserValue, List<Boolean> inFiber) {
        UI.getCurrent().add(f.getPeer());
        List<Object> seen = new ArrayList<>();
        f.addPropertyChangeListener("value", e -> {
            inFiber.add(UIFibers.isInUIFiber());
            seen.add(e.getNewValue());
        });
        LocatorJ._setValue((com.vaadin.flow.component.Component & HasValue<?, V>) f.getPeer(), browserValue);
        return seen;
    }

    @Test
    @DisplayName("a date pick sets the value as a Date, inside a UI fiber")
    void datePick() {
        List<Boolean> inFiber = new ArrayList<>();
        List<Object> seen = browserEdit(new JFormattedTextField(new DateFormatter()),
                LocalDate.of(2026, 4, 11), inFiber);
        Date expected = Date.from(LocalDate.of(2026, 4, 11).atStartOfDay(ZoneOffset.UTC).toInstant());
        assertEquals(List.of(expected), seen);
        assertEquals(List.of(true), inFiber);
    }

    @Test
    @DisplayName("an Integer edit sets the value, inside a UI fiber")
    void integerEdit() {
        List<Boolean> inFiber = new ArrayList<>();
        assertEquals(List.of(42), browserEdit(numberField(Integer.class), 42, inFiber));
        assertEquals(List.of(true), inFiber);
    }

    @Test
    @DisplayName("a Long edit sets the value, inside a UI fiber")
    void longEdit() {
        List<Boolean> inFiber = new ArrayList<>();
        assertEquals(List.of(42L), browserEdit(numberField(Long.class), 42L, inFiber));
        assertEquals(List.of(true), inFiber);
    }

    @Test
    @DisplayName("a Double edit sets the value, inside a UI fiber")
    void numberEdit() {
        List<Boolean> inFiber = new ArrayList<>();
        assertEquals(List.of(4.5), browserEdit(numberField(Double.class), 4.5, inFiber));
        assertEquals(List.of(true), inFiber);
    }

    /** The relay ignores server-side changes, so the field's own push must not come back as a second event. */
    @Test
    @DisplayName("programmatic setValue fires the value property once")
    void programmaticSetValueFiresOnce() {
        JFormattedTextField f = numberField(Integer.class);
        UI.getCurrent().add(f.getPeer());
        List<Object> seen = new ArrayList<>();
        f.addPropertyChangeListener("value", e -> seen.add(e.getNewValue()));
        f.setValue(7);
        assertEquals(List.of(7), seen);
        assertEquals(7, ((HasValue<?, ?>) f.getPeer()).getValue());
    }
}
