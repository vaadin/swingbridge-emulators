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

import com.vaadin.flow.component.ModalityMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;

import java.beans.PropertyChangeEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Bare-AWT Dialog surface tests. JDialog inherits most behaviour and
 * carries its own comprehensive test; this file focuses on the
 * Dialog-specific surface (modal / modalityType / DEFAULT_MODALITY_TYPE)
 * exercised on a {@code vaadinx.awt.Dialog} directly without the
 * Swing-layer JRootPane / contentPane scaffolding.
 *
 * <p>Java has no import alias, so the Vaadin overlay type is spelled out in
 * full at every peer cast — {@code Dialog} here is the emulator.
 */
class DialogTest extends AbstractKaribuTest {

    /** The Vaadin overlay backing {@code d}. */
    private static com.vaadin.flow.component.dialog.Dialog peerOf(Dialog d) {
        return (com.vaadin.flow.component.dialog.Dialog) d.getPeer();
    }

    @Test
    @DisplayName("can instantiate")
    void canInstantiate() {
        new Dialog((Frame) null);
    }

    @Test
    @DisplayName("title defaults to empty string")
    void titleDefaultsToEmptyString() {
        assertEquals("", new Dialog((Frame) null).getTitle());
    }

    @Test
    @DisplayName("default modality type is MODELESS")
    void defaultModalityTypeIsModeless() {
        // AWT contract: the no-arg-modal ctors create a non-modal dialog.
        Dialog d = new Dialog((Frame) null);
        assertEquals(Dialog.ModalityType.MODELESS, d.getModalityType());
        assertFalse(d.isModal());
    }

    @Test
    @DisplayName("setModal true uses DEFAULT_MODALITY_TYPE")
    void setModalTrueUsesDefaultModalityType() {
        Dialog d = new Dialog((Frame) null);
        d.setModal(true);
        assertEquals(Dialog.DEFAULT_MODALITY_TYPE, d.getModalityType());
        assertEquals(Dialog.ModalityType.APPLICATION_MODAL, d.getModalityType());
    }

    @Test
    @DisplayName("setModal false sets MODELESS")
    void setModalFalseSetsModeless() {
        Dialog d = new Dialog((Frame) null, "t", true);
        d.setModal(false);
        assertEquals(Dialog.ModalityType.MODELESS, d.getModalityType());
    }

    @Test
    @DisplayName("setModalityType null coerces to MODELESS")
    void setModalityTypeNullCoercesToModeless() {
        Dialog d = new Dialog((Frame) null);
        d.setModal(true);  // start modal
        d.setModalityType(null);
        assertEquals(Dialog.ModalityType.MODELESS, d.getModalityType());
        assertFalse(d.isModal());
    }

    @Test
    @DisplayName("each ModalityType value collapses to STRICT or MODELESS on the peer")
    void eachModalityTypeValueCollapsesToStrictOrModelessOnThePeer() {
        for (Dialog.ModalityType type : Dialog.ModalityType.values()) {
            Dialog d = new Dialog((Frame) null);
            d.setModalityType(type);
            ModalityMode expected = type == Dialog.ModalityType.MODELESS
                    ? ModalityMode.MODELESS
                    : ModalityMode.STRICT;
            assertEquals(expected, peerOf(d).getModality(), "type=" + type);
        }
    }

    @Test
    @DisplayName("setModal and setModalityType fire no bound property")
    void setModalAndSetModalityTypeFireNoBoundProperty() {
        // "modal" is not a bound property: java.awt.Dialog.setModalityType
        // assigns modalityType and derives the modal bit, and setModal is a
        // two-liner delegating to it. Neither fires (D_property_fanout_audit). The observable
        // contract is the peer's ModalityMode, asserted above.
        Dialog d = new Dialog((Frame) null);
        List<PropertyChangeEvent> events = new ArrayList<>();
        d.addPropertyChangeListener(events::add);
        d.setModal(true);
        d.setModal(false);
        d.setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
        d.setModalityType(Dialog.ModalityType.DOCUMENT_MODAL);
        assertEquals(0, events.stream().filter(it -> "modal".equals(it.getPropertyName())).count());
        assertEquals(0, events.stream().filter(it -> "modalityType".equals(it.getPropertyName())).count());
    }

    @Test
    @DisplayName("Frame-owner ctor registers in owner's owned-window list")
    void frameOwnerCtorRegistersInOwnersOwnedWindowList() {
        Frame frame = new Frame();
        Dialog dialog = new Dialog(frame);
        assertEquals(frame, dialog.getOwner());
        assertTrue(Arrays.stream(frame.getOwnedWindows()).anyMatch(it -> it == dialog));
    }

    @Test
    @DisplayName("Window-owner ctor with ModalityType seeds the type")
    void windowOwnerCtorWithModalityTypeSeedsTheType() {
        Frame frame = new Frame();
        Dialog d = new Dialog(frame, Dialog.ModalityType.DOCUMENT_MODAL);
        assertEquals(Dialog.ModalityType.DOCUMENT_MODAL, d.getModalityType());
        assertTrue(d.isModal());
    }

    @Test
    @DisplayName("setTitle round-trips through Dialog and the peer header")
    void setTitleRoundTripsThroughDialogAndThePeerHeader() {
        Dialog d = new Dialog((Frame) null);
        d.setTitle("Edit");
        assertEquals("Edit", d.getTitle());
        assertEquals("Edit", peerOf(d).getHeaderTitle());
    }

    @Test
    @DisplayName("a null title is stored as null, unlike Frame's, and the peer shows none")
    void nullTitleStaysNull() {
        Dialog d = new Dialog((Frame) null, "initial");
        d.setTitle(null);
        assertNull(d.getTitle());
        assertEquals("", peerOf(d).getHeaderTitle());
        assertNull(new Dialog((Frame) null, (String) null).getTitle(), "a ctor stores null too");
    }

    /**
     * Logs the public setters a constructor reaches, as the JDK script's subclass does — into a
     * static list, since the super constructor runs before any field of this class is assigned.
     */
    private static final List<String> CTOR_LOG = new ArrayList<>();

    private static class LoggingDialog extends Dialog {
        LoggingDialog(Frame owner, String title, boolean modal) {
            super(owner, title, modal);
        }

        LoggingDialog(Window owner, String title, Dialog.ModalityType modalityType) {
            super(owner, title, modalityType);
        }

        @Override public void setTitle(String t) { CTOR_LOG.add("setTitle(" + t + ")"); super.setTitle(t); }
        @Override public void setModal(boolean b) { CTOR_LOG.add("setModal(" + b + ")"); super.setModal(b); }
        @Override public void setModalityType(Dialog.ModalityType t) { CTOR_LOG.add("setModalityType(" + t + ")"); super.setModalityType(t); }
    }

    /** The part of {@code paramString} Dialog appends, after Container's layout manager. */
    private static String dialogParams(Dialog d) {
        String s = d.toString();
        int layout = s.indexOf(",layout=");
        return s.substring(s.indexOf(',', layout + 1), s.length() - 1);
    }

    /**
     * A script measured on JDK 25 (xvfb): which public setters each constructor reaches, the
     * owner check, the title's null and PCE behaviour, modality coercion and paramString.
     */
    @Test
    @DisplayName("the Dialog state bodies replay the JDK's script")
    void stateMatchesTheJdk() {
        CTOR_LOG.clear();
        LoggingDialog d1 = new LoggingDialog((Frame) null, "t", true);
        assertEquals(List.of("setModalityType(APPLICATION_MODAL)"), CTOR_LOG);
        assertEquals("t", d1.getTitle());
        CTOR_LOG.clear();
        LoggingDialog d2 = new LoggingDialog((Frame) null, null, false);
        assertEquals(List.of("setModalityType(MODELESS)"), CTOR_LOG);
        assertNull(d2.getTitle());
        assertEquals(",MODELESS", dialogParams(d2));
        CTOR_LOG.clear();
        LoggingDialog d3 = new LoggingDialog((Window) null, "w", Dialog.ModalityType.DOCUMENT_MODAL);
        assertEquals(List.of("setModalityType(DOCUMENT_MODAL)"), CTOR_LOG);
        assertTrue(d3.isModal());

        Frame f = new Frame();
        Window w = new Window(f);
        IllegalArgumentException wrongOwner = assertThrows(IllegalArgumentException.class, () -> new Dialog(w));
        assertEquals("Wrong parent window", wrongOwner.getMessage());

        Dialog d = new Dialog(f);
        assertTrue(d.isResizable());
        assertEquals("", d.getTitle());
        assertEquals(",MODELESS,title=", dialogParams(d));
        List<String> pce = new ArrayList<>();
        d.addPropertyChangeListener(e -> pce.add(e.getPropertyName() + ":" + e.getOldValue() + "->" + e.getNewValue()));
        d.setTitle("");
        d.setTitle("x");
        d.setTitle("x");
        d.setTitle(null);
        d.setTitle(null);
        d.setTitle("");
        d.setModal(true);
        d.setModalityType(Dialog.ModalityType.DOCUMENT_MODAL);
        d.setModalityType(null);
        d.setResizable(false);
        d.setResizable(false);
        assertEquals(List.of("title:->x", "title:x->null", "title:null->null", "title:null->"), pce);
        assertFalse(d.isModal());
        assertEquals(Dialog.ModalityType.MODELESS, d.getModalityType());
        assertFalse(d.isResizable());
        d.setModal(true);
        assertEquals(Dialog.ModalityType.APPLICATION_MODAL, d.getModalityType());
        d.setModalityType(Dialog.ModalityType.TOOLKIT_MODAL);
        assertEquals(",TOOLKIT_MODAL,title=", dialogParams(d));
    }

    @Test
    @DisplayName("setResizable drives the native Vaadin Dialog resize flag")
    void setResizableDrivesTheNativeVaadinDialogResizeFlag() {
        // Regression: a resizable Swing dialog must stay resizable as a
        // Vaadin Dialog overlay. AWT default is true; the SFrame peer seeds
        // the native flag, so a dialog that never calls setResizable still
        // renders resize grips.
        Dialog d = new Dialog((Frame) null);
        assertTrue(d.isResizable(), "AWT default is resizable");
        assertTrue(peerOf(d).isResizable(), "seeded onto the peer");

        d.setResizable(false);
        assertFalse(d.isResizable());
        assertFalse(peerOf(d).isResizable(), "request reaches the peer");

        d.setResizable(true);
        assertTrue(d.isResizable());
        assertTrue(peerOf(d).isResizable());
    }

    @Test
    @DisplayName("peer is draggable by default to match always-movable AWT windows")
    void peerIsDraggableByDefaultToMatchAlwaysMovableAwtWindows() {
        // Swing has no setDraggable — windows are always user-movable. The
        // SFrame peer seeds Vaadin Dialog's native draggable flag true.
        assertTrue(peerOf(new Dialog((Frame) null)).isDraggable());
    }
}
