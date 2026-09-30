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

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.icon.Icon;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SHelper;
import com.vaadin.swingbridge.surrogates.SJInternalFrame;
import vaadinx.AbstractKaribuTest;
import vaadinx.Counter;
import vaadinx.EHelper;
import vaadinx.swing.event.InternalFrameAdapter;
import vaadinx.swing.event.InternalFrameEvent;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

class JInternalFrameTest extends AbstractKaribuTest {

    private final List<String> warns = new ArrayList<>();

    @BeforeEach
    void installWarnHook() {
        warns.clear();
        EHelper.warnHook = warns::add;
        SHelper.warnHook = warns::add;
    }

    @AfterEach
    void resetWarnHook() {
        EHelper.warnHook = msg -> { };
        SHelper.warnHook = msg -> { };
    }

    private static SJInternalFrame peerOf(JInternalFrame f) {
        return (SJInternalFrame) f.getPeer();
    }

    /** The title-bar glyphs, keyed by aria-label ("Minimize" / "Maximize" / "Close"). */
    private static Map<String, Icon> headerIcons(JInternalFrame f) {
        Map<String, Icon> byLabel = new LinkedHashMap<>();
        peerOf(f).getHeader().getElement().getChildren().forEach(el ->
                el.getComponent()
                        .filter(Icon.class::isInstance)
                        .map(Icon.class::cast)
                        .ifPresent(icon -> byLabel.put(icon.getElement().getAttribute("aria-label"), icon)));
        return byLabel;
    }

    @Test
    @DisplayName("can instantiate")
    void canInstantiate() {
        new JInternalFrame("Doc");
    }

    @Test
    @DisplayName("R_leaf_peer_lockdown lock-down peer is SJInternalFrame")
    void peerIsSJInternalFrame() {
        assertInstanceOf(SJInternalFrame.class, new JInternalFrame("Doc").getPeer());
    }

    @Test
    @DisplayName("default close operation is DISPOSE_ON_CLOSE")
    void defaultCloseOperationIsDispose() {
        assertEquals(JInternalFrame.DISPOSE_ON_CLOSE,
                new JInternalFrame("Doc").getDefaultCloseOperation());
    }

    @Test
    @DisplayName("starts invisible, setVisible opens the overlay and fires OPENED")
    void setVisibleOpensAndFires() {
        JInternalFrame f = new JInternalFrame("Doc");
        assertFalse(f.isVisible());
        Counter opened = new Counter();
        f.addInternalFrameListener(new InternalFrameAdapter() {
            @Override
            public void internalFrameOpened(InternalFrameEvent e) {
                opened.inc();
            }
        });
        f.setVisible(true);
        assertTrue(f.isVisible());
        opened.assertEquals(1);
        assertTrue(peerOf(f).isOpened());
    }

    @Test
    @DisplayName("setSelected is vetoable and fires ACTIVATED via the relay")
    void setSelectedFiresActivated() throws PropertyVetoException {
        JInternalFrame f = onAShowingDesktop("Doc");
        Counter activated = new Counter();
        f.addInternalFrameListener(new InternalFrameAdapter() {
            @Override
            public void internalFrameActivated(InternalFrameEvent e) {
                activated.inc();
            }
        });
        f.setSelected(true);
        assertTrue(f.isSelected());
        activated.assertEquals(1);
    }

    @Test
    @DisplayName("selecting a frame that is not showing is refused, and silently")
    void selectingANonShowingFrameIsRefused() throws PropertyVetoException {
        // The JDK's gate is `selected && !isShowing()`, and it precedes the
        // vetoable change — so a refused select fires nothing at all. Measured
        // on JDK 25 for all three ways a frame can fail to be showing.
        JInternalFrame lone = new JInternalFrame("lone");
        lone.setVisible(true);
        assertFalse(lone.isShowing(), "visible, but on no desktop");
        lone.setSelected(true);
        assertFalse(lone.isSelected());

        JDesktopPane orphan = new JDesktopPane();
        JInternalFrame onOrphan = new JInternalFrame("onOrphan");
        onOrphan.setVisible(true);
        orphan.add(onOrphan);
        assertFalse(onOrphan.isShowing(), "on a desktop that is in no window");
        onOrphan.setSelected(true);
        assertFalse(onOrphan.isSelected());

        JInternalFrame neverShown = new JInternalFrame("neverShown");
        showingDesktop().add(neverShown);
        assertFalse(neverShown.isShowing(), "on a showing desktop, but never made visible");
        neverShown.setSelected(true);
        assertFalse(neverShown.isSelected());
    }

    @Test
    @DisplayName("deselecting passes even when the frame has stopped showing")
    void deselectingIsNotGatedOnShowing() throws PropertyVetoException {
        // Deliberately asymmetric in the JDK: "We may deselect even if neither
        // is showing." Measured — the whole chain fires, DEACTIVATED included.
        JInternalFrame f = onAShowingDesktop("Doc");
        f.setSelected(true);
        assertTrue(f.isSelected());

        Counter deactivated = new Counter();
        f.addInternalFrameListener(new InternalFrameAdapter() {
            @Override
            public void internalFrameDeactivated(InternalFrameEvent e) {
                deactivated.inc();
            }
        });
        f.setVisible(false);
        assertFalse(f.isShowing());
        f.setSelected(false);
        assertFalse(f.isSelected(), "deselect is not gated on showing");
        deactivated.assertEquals(1);
    }

    /** A frame that {@code isShowing()}: visible, on a desktop, in a shown frame. */
    private JInternalFrame onAShowingDesktop(String title) {
        JInternalFrame f = new JInternalFrame(title);
        f.setVisible(true);
        showingDesktop().add(f);
        return f;
    }

    private JDesktopPane showingDesktop() {
        JFrame host = new JFrame("host");
        JDesktopPane desktop = new JDesktopPane();
        host.setContentPane(desktop);
        host.setVisible(true);
        return desktop;
    }

    @Test
    @DisplayName("a veto aborts the constrained change with no state mutation")
    void aVetoAbortsTheChange() {
        JInternalFrame f = new JInternalFrame("Doc");
        f.addVetoableChangeListener(evt -> {
            if (JInternalFrame.IS_MAXIMUM_PROPERTY.equals(evt.getPropertyName())) {
                throw new PropertyVetoException("no", evt);
            }
        });
        assertThrows(PropertyVetoException.class, () -> f.setMaximum(true));
        assertFalse(f.isMaximum(), "vetoed change must leave state unchanged");
    }

    @Test
    @DisplayName("maximize drives the peer geometry")
    void maximizeDrivesPeerGeometry() throws PropertyVetoException {
        JInternalFrame f = new JInternalFrame("Doc");
        f.setVisible(true);
        f.setMaximum(true);
        SJInternalFrame peer = peerOf(f);
        assertEquals("100%", peer.getWidth());
        assertEquals("100%", peer.getHeight());
        assertTrue(f.isMaximum());
    }

    @Test
    @DisplayName("setClosed disposes the frame and fires CLOSED")
    void setClosedDisposesAndFires() throws PropertyVetoException {
        JInternalFrame f = new JInternalFrame("Doc");
        f.setVisible(true);
        Counter closed = new Counter();
        f.addInternalFrameListener(new InternalFrameAdapter() {
            @Override
            public void internalFrameClosed(InternalFrameEvent e) {
                closed.inc();
            }
        });
        f.setClosed(true);
        assertTrue(f.isClosed());
        closed.assertEquals(1);
    }

    // --- Minimize is emulator-complete (D_internalframe_minimize) ----------------------------

    @Test
    @DisplayName("iconify is emulator-complete — state, events, desktop icon, no WARN")
    void iconifyIsEmulatorComplete() throws PropertyVetoException {
        JInternalFrame f = new JInternalFrame("Doc");
        f.setVisible(true);
        List<Integer> events = new ArrayList<>();
        f.addInternalFrameListener(new InternalFrameAdapter() {
            @Override
            public void internalFrameIconified(InternalFrameEvent e) {
                events.add(e.getID());
            }

            @Override
            public void internalFrameDeiconified(InternalFrameEvent e) {
                events.add(e.getID());
            }
        });
        assertNotNull(f.getDesktopIcon(), "getDesktopIcon returns a real holder for API completeness");

        f.setIcon(true);
        assertTrue(f.isIcon());
        assertFalse(peerOf(f).isOpened(), "iconified frame's overlay is hidden");

        f.setIcon(false);
        assertFalse(f.isIcon());
        assertTrue(peerOf(f).isOpened(), "deiconify restores the overlay");

        assertEquals(
                List.of(InternalFrameEvent.INTERNAL_FRAME_ICONIFIED,
                        InternalFrameEvent.INTERNAL_FRAME_DEICONIFIED),
                events);
        // Emulator-complete: minimize must not emit an onUnimplemented WARN,
        // else the Sampler exit gate would trip (D_internalframe_minimize).
        assertNoWarns(warns, "unexpected WARNs: " + warns);
    }

    @Test
    @DisplayName("HIDE_ON_CLOSE default action hides the frame")
    void hideOnCloseHidesTheFrame() {
        JInternalFrame f = new JInternalFrame("Doc");
        f.setDefaultCloseOperation(JInternalFrame.HIDE_ON_CLOSE);
        f.setVisible(true);
        f.doDefaultCloseAction();
        assertFalse(f.isVisible());
        assertFalse(f.isClosed(), "HIDE_ON_CLOSE hides but does not close");
    }

    @Test
    @DisplayName("ctor flags drive the peer title-bar controls")
    void ctorFlagsDriveTitleBarControls() {
        // Fully decorated — all three title-bar buttons visible.
        Map<String, Icon> full = headerIcons(new JInternalFrame("Doc", true, true, true, true));
        assertTrue(full.get("Minimize").isVisible());
        assertTrue(full.get("Maximize").isVisible());
        assertTrue(full.get("Close").isVisible());

        // Bare ctor — JInternalFrame() defaults every flag false, so no chrome.
        Map<String, Icon> bare = headerIcons(new JInternalFrame());
        assertFalse(bare.get("Minimize").isVisible());
        assertFalse(bare.get("Maximize").isVisible());
        assertFalse(bare.get("Close").isVisible());
    }

    @Test
    @DisplayName("header minimize button drives vetoable setIcon")
    void headerMinimizeDrivesSetIcon() {
        JInternalFrame f = new JInternalFrame("Doc", true, true, true, true);
        f.setVisible(true);
        Counter iconified = new Counter();
        f.addInternalFrameListener(new InternalFrameAdapter() {
            @Override
            public void internalFrameIconified(InternalFrameEvent e) {
                iconified.inc();
            }
        });
        Icon minimize = headerIcons(f).get("Minimize");
        ComponentUtil.fireEvent(minimize, new ClickEvent<>(minimize));
        assertTrue(f.isIcon(), "header minimize should drive setIcon(true)");
        iconified.assertEquals(1);
    }

    @Test
    @DisplayName("add routes into the content pane")
    void addRoutesIntoTheContentPane() {
        JInternalFrame f = new JInternalFrame("Doc");
        JButton child = new JButton("x");
        f.add(child);
        assertSame(f.getContentPane(), child.getParent());
    }

    @Test
    @DisplayName("setIconifiable fires the JDK's iconable property, not iconifiable")
    void setIconifiableFiresIconable() {
        // JInternalFrame's setter and its field disagree in the JDK — the
        // setter is setIconifiable, the field (and so the event) is
        // "iconable" — and the values are boxed Booleans because the JDK
        // boxes explicitly rather than using the (boolean, boolean) overload
        // (D_property_fanout_audit). A migrated app listening for "iconable" heard nothing.
        JInternalFrame f = new JInternalFrame("t");
        List<PropertyChangeEvent> events = new ArrayList<>();
        f.addPropertyChangeListener(events::add);

        f.setIconifiable(true);

        assertEquals(0, events.stream().filter(e -> "iconifiable".equals(e.getPropertyName())).count());
        PropertyChangeEvent iconable = assertSingle(
                events.stream().filter(e -> "iconable".equals(e.getPropertyName())).toList());
        assertEquals(Boolean.FALSE, iconable.getOldValue());
        assertEquals(Boolean.TRUE, iconable.getNewValue());
        assertTrue(f.isIconifiable());
    }
}
