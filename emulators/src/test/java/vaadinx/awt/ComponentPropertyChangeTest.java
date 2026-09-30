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

package vaadinx.awt;

import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;

import java.awt.Color;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ComponentPropertyChangeTest extends AbstractKaribuTest {

    private Component newComponent() {
        return new Component(new Div()) {
        };
    }

    private static final class RecordingListener implements PropertyChangeListener {
        final List<PropertyChangeEvent> events = new ArrayList<>();

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            events.add(evt);
        }
    }

    @Test
    @DisplayName("setForeground fires foreground property change with correct old and new values")
    void setForegroundFiresForegroundPropertyChangeWithCorrectOldAndNewValues() {
        Component c = newComponent();
        RecordingListener listener = new RecordingListener();
        c.addPropertyChangeListener(listener);

        c.setForeground(Color.RED);
        c.setForeground(Color.BLUE);

        assertEquals(2, listener.events.size());
        PropertyChangeEvent first = listener.events.get(0);
        assertEquals("foreground", first.getPropertyName());
        assertNull(first.getOldValue());
        assertEquals(Color.RED, first.getNewValue());
        assertSame(c, first.getSource());
        PropertyChangeEvent second = listener.events.get(1);
        assertEquals("foreground", second.getPropertyName());
        assertEquals(Color.RED, second.getOldValue());
        assertEquals(Color.BLUE, second.getNewValue());
    }

    @Test
    @DisplayName("named listener only receives events for its property")
    void namedListenerOnlyReceivesEventsForItsProperty() {
        Component c = newComponent();
        RecordingListener nameListener = new RecordingListener();
        c.addPropertyChangeListener("name", nameListener);

        c.setName("ok");
        c.setLocale(Locale.ENGLISH);

        assertEquals(1, nameListener.events.size());
        assertEquals("name", nameListener.events.get(0).getPropertyName());
    }

    @Test
    @DisplayName("removePropertyChangeListener stops delivery")
    void removePropertyChangeListenerStopsDelivery() {
        Component c = newComponent();
        RecordingListener listener = new RecordingListener();
        c.addPropertyChangeListener(listener);
        c.setName("first");
        c.removePropertyChangeListener(listener);
        c.setName("second");

        assertEquals(1, listener.events.size());
        assertEquals("first", listener.events.get(0).getNewValue());
    }

    @Test
    @DisplayName("getPropertyChangeListeners reflects current registrations")
    void getPropertyChangeListenersReflectsCurrentRegistrations() {
        Component c = newComponent();
        RecordingListener global = new RecordingListener();
        RecordingListener named = new RecordingListener();
        c.addPropertyChangeListener(global);
        c.addPropertyChangeListener("name", named);

        assertEquals(2, c.getPropertyChangeListeners().length);
        assertEquals(1, c.getPropertyChangeListeners("name").length);
    }

    @Test
    @DisplayName("equal old and new values do not fire")
    void equalOldAndNewValuesDoNotFire() {
        Component c = newComponent();
        c.setForeground(Color.RED);
        RecordingListener listener = new RecordingListener();
        c.addPropertyChangeListener(listener);

        // Re-setting to an equal (but not same) Color must still be skipped —
        // PropertyChangeSupport deduplicates by .equals.
        c.setForeground(new Color(255, 0, 0));

        assertEquals(0, listener.events.size());
    }
}
