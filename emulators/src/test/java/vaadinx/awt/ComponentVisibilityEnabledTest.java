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
import com.vaadin.flow.component.html.Hr;
import com.vaadin.flow.component.html.NativeButton;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.awt.event.ComponentAdapter;
import vaadinx.awt.event.ComponentEvent;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentVisibilityEnabledTest extends AbstractKaribuTest {

    private static final class Recorder implements PropertyChangeListener {
        final List<PropertyChangeEvent> events = new ArrayList<>();

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            events.add(evt);
        }

        /** How many of the recorded events carry {@code name} as their property. */
        int countOf(String name) {
            return (int) events.stream().filter(it -> name.equals(it.getPropertyName())).count();
        }
    }

    private Component over(com.vaadin.flow.component.Component peer) {
        return new Component(peer) {
        };
    }

    // --- Visibility ---

    @Test
    @DisplayName("isVisible defaults to true")
    void isVisibleDefaultsToTrue() {
        Component c = over(new Div());
        assertTrue(c.isVisible());
    }

    @Test
    @DisplayName("setVisible toggles peer and fires no bound property")
    void setVisibleTogglesPeerAndFiresNoBoundProperty() {
        // AWT signals visibility with a ComponentEvent and nothing else —
        // there is no "visible" bound property on Component. JPopupMenu is the
        // sole class in java.awt/javax.swing that fires one. SB-Emulators used to fire
        // it from Component.setVisible, handing every migrated component a
        // PropertyChangeEvent it never saw on the desktop (R_decline_effect_only,
        // W_property_fanout). The COMPONENT_SHOWN/HIDDEN contract is asserted
        // by the next test, which is where it always belonged.
        Div peer = new Div();
        Component c = over(peer);
        Recorder r = new Recorder();
        c.addPropertyChangeListener(r);

        c.setVisible(false);
        assertFalse(c.isVisible());
        assertFalse(peer.isVisible());

        c.setVisible(true);
        assertTrue(c.isVisible());
        assertTrue(peer.isVisible());

        assertEquals(0, r.countOf("visible"), r.events.toString());
    }

    @Test
    @DisplayName("setVisible fires COMPONENT_SHOWN and COMPONENT_HIDDEN")
    void setVisibleFiresComponentShownAndComponentHidden() {
        Component c = over(new Div());
        List<ComponentEvent> events = new ArrayList<>();
        c.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                events.add(e);
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                events.add(e);
            }
        });

        c.setVisible(false);
        c.setVisible(true);

        assertEquals(2, events.size());
        assertEquals(ComponentEvent.COMPONENT_HIDDEN, events.get(0).getID());
        assertEquals(c, events.get(0).getComponent());
        assertEquals(ComponentEvent.COMPONENT_SHOWN, events.get(1).getID());
    }

    @Test
    @DisplayName("setVisible does not fire ComponentEvent on no-op")
    void setVisibleDoesNotFireComponentEventOnNoOp() {
        Component c = over(new Div());
        List<ComponentEvent> events = new ArrayList<>();
        c.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                events.add(e);
            }

            @Override
            public void componentHidden(ComponentEvent e) {
                events.add(e);
            }
        });

        c.setVisible(true);  // already true — no change, no fire
        assertEquals(0, events.size());
    }

    @Test
    @DisplayName("deprecated hide and show delegate to setVisible")
    void deprecatedHideAndShowDelegateToSetVisible() {
        Div peer = new Div();
        Component c = over(peer);
        c.hide();
        assertFalse(peer.isVisible());
        c.show();
        assertTrue(peer.isVisible());
        c.show(false);
        assertFalse(peer.isVisible());
    }

    // --- Enabled (HasEnabled peer) ---

    @Test
    @DisplayName("setEnabled on HasEnabled peer toggles peer and fires no bound property")
    void setEnabledOnHasEnabledPeerTogglesPeerAndFiresNoBoundProperty() {
        // The bound "enabled" property is JComponent's, not Component's:
        // java.awt.Component.setEnabled delegates to the deprecated enable(),
        // which fires only the AccessibleContext property. SB-Emulators used to fire it
        // here, so every AWT-level emulator (Button, Label, Panel, Checkbox,
        // …) handed migrated code a PropertyChangeEvent the desktop never
        // sends (D_property_fanout_audit). JComponentEnabledPceTest pins the other side.
        NativeButton peer = new NativeButton();
        Component c = over(peer);
        Recorder r = new Recorder();
        c.addPropertyChangeListener(r);

        c.setEnabled(false);
        assertFalse(c.isEnabled());
        assertFalse(peer.isEnabled());

        c.setEnabled(true);
        assertTrue(c.isEnabled());
        assertTrue(peer.isEnabled());

        assertEquals(0, r.countOf("enabled"));
    }

    // --- Enabled (non-HasEnabled peer, the corner case) ---

    // Hr extends HtmlComponent directly (no HasComponents mix-in) so it does
    // not implement HasEnabled — the rare "can't disable this" peer shape.

    @Test
    @DisplayName("setEnabled false on non-HasEnabled peer leaves isEnabled true and fires no event")
    void setEnabledFalseOnNonHasEnabledPeerLeavesIsEnabledTrueAndFiresNoEvent() {
        Component c = over(new Hr());
        Recorder r = new Recorder();
        c.addPropertyChangeListener(r);

        c.setEnabled(false);

        // onUnsupported was called (a WARN log); field unchanged, no event.
        assertTrue(c.isEnabled());
        assertEquals(0, r.countOf("enabled"));
    }

    @Test
    @DisplayName("setEnabled true on non-HasEnabled peer is a no-op")
    void setEnabledTrueOnNonHasEnabledPeerIsANoOp() {
        Component c = over(new Hr());
        Recorder r = new Recorder();
        c.addPropertyChangeListener(r);

        c.setEnabled(true);

        assertTrue(c.isEnabled());
        assertEquals(0, r.countOf("enabled"));
    }

    @Test
    @DisplayName("deprecated enable and disable delegate to setEnabled")
    void deprecatedEnableAndDisableDelegateToSetEnabled() {
        NativeButton peer = new NativeButton();
        Component c = over(peer);
        c.disable();
        assertFalse(peer.isEnabled());
        c.enable();
        assertTrue(peer.isEnabled());
        c.enable(false);
        assertFalse(peer.isEnabled());
    }
}
