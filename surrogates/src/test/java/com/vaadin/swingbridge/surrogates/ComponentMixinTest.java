/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.swingbridge.surrogates;

import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.dom.Style;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.awt.ComponentMixin;
import com.vaadin.swingbridge.surrogates.awt.event.SComponentAdapter;
import com.vaadin.swingbridge.surrogates.awt.event.SComponentEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SContainerAdapter;
import com.vaadin.swingbridge.surrogates.awt.event.SContainerEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SFocusAdapter;
import com.vaadin.swingbridge.surrogates.awt.event.SFocusEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SHierarchyBoundsAdapter;
import com.vaadin.swingbridge.surrogates.awt.event.SHierarchyEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SHierarchyListener;
import com.vaadin.swingbridge.surrogates.awt.event.SKeyAdapter;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseAdapter;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseEvent;
import com.vaadin.swingbridge.surrogates.awt.event.SMouseListener;
import com.vaadin.swingbridge.surrogates.internal.NameStore;
import com.vaadin.swingbridge.surrogates.util.CssConvert;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputMethodEvent;
import java.awt.event.InputMethodListener;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The AWT-level {@link ComponentMixin} surface under the
 * Vaadin-first stance (SD_vaadin_first_binding), driven through a Button-backed {@link TestSurrogate}
 * (and {@link DivContainerSurrogate} where a Div host is needed):
 *
 * <ul>
 *  <li>enabled / name (NameStore, never Vaadin id) / foreground / background /
 *      font — CSS round-trip (lossy per SD_border_css_lossy) with auto-PCE on mapped setters (SD_auto_pce);
 *      the PropertyChangeListener family; tooltip + focus delegation.
 *  <li>size / bounds / preferred-min-max via HasSize; cursor via CSS; isShowing
 *      via UI attachment; layout → flex/grid CSS on a Div container.
 *  <li>the listener wiring: SComponentListener / SFocusListener / SHierarchyListener
 *      wired eagerly to peer events; mouse-click via ClickNotifier; SContainerListener
 *      and SHierarchyBoundsListener are Bucket B onNoop (SD_listeners_without_analog).
 *  <li>the SD_api_three_buckets three-bucket partition: Bucket A implemented, Bucket B onNoop (no
 *      WARN), Bucket C WARN (mouse-motion / wheel / input-method — no Vaadin analog).
 * </ul>
 *
 * Key-listener wiring lives in {@link ComponentMixinKeyListenerTest}; the
 * JComponent-level surface in {@link JComponentMixinTest}.
 */
class ComponentMixinTest extends AbstractKaribuTest {

    /**
     * A surrogate whose peer is the abstract {@code Component} — deliberately
     * not a ClickNotifier, so it exercises the WARN-and-skip branch.
     */
    @Tag("svg")
    private static class NonClickSurrogate extends Component implements ComponentMixin {
    }

    /** A LayoutManager SB-Emulators does not recognise, so nothing dispatches CSS. */
    private static LayoutManager customLayout() {
        return new LayoutManager() {
            @Override
            public void addLayoutComponent(String name, java.awt.Component comp) {
            }

            @Override
            public void removeLayoutComponent(java.awt.Component comp) {
            }

            @Override
            public Dimension preferredLayoutSize(Container p) {
                return new Dimension(1, 1);
            }

            @Override
            public Dimension minimumLayoutSize(Container p) {
                return new Dimension(1, 1);
            }

            @Override
            public void layoutContainer(Container p) {
            }
        };
    }

    /** Subscribes an unfiltered PCE listener and returns the list events land in. */
    private static List<PropertyChangeEvent> recordPces(TestSurrogate s) {
        final List<PropertyChangeEvent> received = new ArrayList<>();
        s.addPropertyChangeListener(received::add);
        return received;
    }

    /** Subscribes a click-only mouse listener and returns the list events land in. */
    private static List<SMouseEvent> recordClicks(ComponentMixin s) {
        final List<SMouseEvent> received = new ArrayList<>();
        s.addMouseListener(new SMouseAdapter() {
            @Override
            public void mouseClicked(SMouseEvent e) {
                received.add(e);
            }
        });
        return received;
    }

    // --- HasEnabled inheritance --------------------------------------

    @Test
    @DisplayName("setEnabled false propagates to HasEnabled")
    void setEnabledPropagates() {
        final TestSurrogate s = new TestSurrogate();
        assertTrue(s.isEnabled());
        s.setEnabled(false);
        assertFalse(s.isEnabled());
        assertNotNull(s.getElement().getAttribute("disabled"));
    }

    // --- name (NameStore) — round-trip without touching Vaadin id ----

    @Test
    @DisplayName("setName round-trips via NameStore and does not touch Vaadin id")
    void setNameDoesNotTouchId() {
        final TestSurrogate s = new TestSurrogate();
        s.setName("login-button");
        assertEquals("login-button", s.getName());
        assertNull(s.getId().orElse(null));
    }

    // --- foreground / background / font (CSS, lossy round-trip) -----

    @Test
    @DisplayName("setForeground writes CSS and round-trips via colorFromCss")
    void setForegroundRoundTrips() {
        final TestSurrogate s = new TestSurrogate();
        s.setForeground(Color.RED);
        assertEquals("rgb(255,0,0)", s.getElement().getStyle().get("color"));
        assertEquals(Color.RED, s.getForeground());
    }

    @Test
    @DisplayName("setForeground with alpha round-trips within one bit")
    void setForegroundAlphaRoundTrips() {
        final TestSurrogate s = new TestSurrogate();
        s.setForeground(new Color(200, 100, 50, 128));
        final Color back = s.getForeground();
        assertNotNull(back);
        assertEquals(200, back.getRed());
        assertEquals(100, back.getGreen());
        assertEquals(50, back.getBlue());
        // Alpha lossy by ≤1 bit through string formatting.
        assertTrue(Math.abs(back.getAlpha() - 128) <= 1);
    }

    @Test
    @DisplayName("setBackground writes CSS background-color")
    void setBackgroundWritesCss() {
        final TestSurrogate s = new TestSurrogate();
        s.setBackground(new Color(0, 128, 255));
        assertEquals("rgb(0,128,255)", s.getElement().getStyle().get("background-color"));
        assertEquals(new Color(0, 128, 255), s.getBackground());
    }

    @Test
    @DisplayName("setFont writes four CSS keys and round-trips via fontFromCss")
    void setFontRoundTrips() {
        final TestSurrogate s = new TestSurrogate();
        s.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 14));
        final Style style = s.getElement().getStyle();
        assertEquals("serif", style.get("font-family"));
        assertEquals("14px", style.get("font-size"));
        assertEquals("bold", style.get("font-weight"));
        assertEquals("italic", style.get("font-style"));
        final Font back = s.getFont();
        assertNotNull(back);
        assertEquals("Serif", back.getFamily());
        assertEquals(14, back.getSize());
        assertTrue(back.isBold());
        assertTrue(back.isItalic());
    }

    @Test
    @DisplayName("getFont returns a non-null default when no font CSS is set")
    void getFontFallsBackToDefault() {
        // Real Swing installs an L&F default font at construction, so getFont()
        // is non-null; we reconstruct from CSS and fall back to
        // CssConvert.DEFAULT_FONT (Dialog/plain/12) when the four font-* slots are
        // all unset. Guards the GUI-builder getFont().deriveFont(...) idiom.
        final TestSurrogate s = new TestSurrogate();
        assertEquals(new Font(Font.DIALOG, Font.PLAIN, 12), s.getFont());
        // A fallback, not written-through CSS.
        assertNull(s.getElement().getStyle().get("font-family"));
    }

    @Test
    @DisplayName("getFont survives the NetBeans deriveFont idiom without NPE")
    void getFontSurvivesDeriveFont() {
        final TestSurrogate s = new TestSurrogate();
        s.setFont(s.getFont().deriveFont(s.getFont().getStyle() | Font.BOLD, s.getFont().getSize() - 2f));
        assertTrue(s.getFont().isBold());
        assertEquals(10, s.getFont().getSize());
    }

    @Test
    @DisplayName("setForeground null removes CSS color")
    void setForegroundNullRemovesCss() {
        final TestSurrogate s = new TestSurrogate();
        s.setForeground(Color.RED);
        s.setForeground(null);
        assertNull(s.getElement().getStyle().get("color"));
        assertNull(s.getForeground());
    }

    @Test
    @DisplayName("getForeground on unset element returns null")
    void getForegroundUnsetIsNull() {
        assertNull(new TestSurrogate().getForeground());
    }

    // --- PCE family (PceSupport) -------------------------------------

    @Test
    @DisplayName("addPropertyChangeListener plus firePropertyChange delivers event")
    void firePropertyChangeDelivers() {
        final TestSurrogate s = new TestSurrogate();
        final List<PropertyChangeEvent> received = recordPces(s);

        s.firePropertyChange("foo", "old", "new");
        assertEquals(1, received.size());
        assertEquals("foo", received.get(0).getPropertyName());
        assertEquals("old", received.get(0).getOldValue());
        assertEquals("new", received.get(0).getNewValue());
        assertSame(s, received.get(0).getSource());
    }

    @Test
    @DisplayName("named property listener filters on property name")
    void namedListenerFilters() {
        final TestSurrogate s = new TestSurrogate();
        final List<PropertyChangeEvent> received = new ArrayList<>();
        s.addPropertyChangeListener("watched", received::add);

        s.firePropertyChange("ignored", 1, 2);
        s.firePropertyChange("watched", 3, 4);
        assertEquals(1, received.size());
        assertEquals("watched", received.get(0).getPropertyName());
    }

    @Test
    @DisplayName("removePropertyChangeListener stops further deliveries")
    void removePropertyChangeListenerStops() {
        final TestSurrogate s = new TestSurrogate();
        final List<PropertyChangeEvent> received = new ArrayList<>();
        final PropertyChangeListener listener = received::add;
        s.addPropertyChangeListener(listener);
        s.firePropertyChange("a", 1, 2);
        s.removePropertyChangeListener(listener);
        s.firePropertyChange("a", 2, 3);
        assertEquals(1, received.size());
    }

    // --- Auto-PCE on mapped setters (SD_auto_pce) ---------------------------

    @Test
    @DisplayName("setName auto-fires name PCE")
    void setNameAutoFiresPce() {
        final TestSurrogate s = new TestSurrogate();
        final List<PropertyChangeEvent> received = recordPces(s);

        s.setName("login-button");
        assertEquals(1, received.size());
        assertEquals("name", received.get(0).getPropertyName());
        assertNull(received.get(0).getOldValue());
        assertEquals("login-button", received.get(0).getNewValue());
    }

    @Test
    @DisplayName("setEnabled fires no enabled PCE at the AWT Component level")
    void setEnabledFiresNoPceAtAwtLevel() {
        // The bound "enabled" property is JComponent's override, not
        // java.awt.Component's — Component.setEnabled delegates to the
        // deprecated enable(), which fires only
        // AccessibleContext.ACCESSIBLE_STATE_PROPERTY on the accessible
        // context's own list, which addPropertyChangeListener never reaches.
        // TestSurrogate is a ContainerMixin (AWT level), so it stays silent;
        // the JComponent-level counterpart is asserted in JComponentMixinTest
        // (SD_property_fanout_audit). This asserted the event at the wrong level.
        final TestSurrogate s = new TestSurrogate();
        final List<PropertyChangeEvent> received = recordPces(s);

        s.setEnabled(false);
        assertFalse(s.isEnabled());
        assertEquals(List.of(),
                received.stream().filter(e -> "enabled".equals(e.getPropertyName())).toList());
    }

    @Test
    @DisplayName("setForeground auto-fires foreground PCE")
    void setForegroundAutoFiresPce() {
        final TestSurrogate s = new TestSurrogate();
        final List<PropertyChangeEvent> received = recordPces(s);

        s.setForeground(Color.RED);
        assertEquals(1, received.size());
        assertEquals("foreground", received.get(0).getPropertyName());
        assertNull(received.get(0).getOldValue());
        assertEquals(Color.RED, received.get(0).getNewValue());
    }

    @Test
    @DisplayName("setBackground auto-fires background PCE")
    void setBackgroundAutoFiresPce() {
        final TestSurrogate s = new TestSurrogate();
        final List<PropertyChangeEvent> received = recordPces(s);

        s.setBackground(new Color(0, 128, 255));
        assertEquals(1, received.size());
        assertEquals("background", received.get(0).getPropertyName());
        assertNull(received.get(0).getOldValue());
        assertEquals(new Color(0, 128, 255), received.get(0).getNewValue());
    }

    @Test
    @DisplayName("setFont auto-fires font PCE")
    void setFontAutoFiresPce() {
        final TestSurrogate s = new TestSurrogate();
        final List<PropertyChangeEvent> received = recordPces(s);

        final Font f = new Font("Serif", Font.BOLD, 14);
        s.setFont(f);
        assertEquals(1, received.size());
        assertEquals("font", received.get(0).getPropertyName());
        // Old value is the non-null L&F-stand-in default (getFont() never
        // returns null now), not null as before the DEFAULT_FONT fallback.
        assertEquals(CssConvert.DEFAULT_FONT, received.get(0).getOldValue());
        assertEquals(f, received.get(0).getNewValue());
    }

    @Test
    @DisplayName("no-op sets do not fire PCE (equal old and new)")
    void noOpSetsFireNoPce() {
        final TestSurrogate s = new TestSurrogate();
        s.setName("foo");
        s.setForeground(Color.RED);
        s.setBackground(Color.BLUE);
        s.setFont(new Font("SansSerif", Font.PLAIN, 12));

        final List<PropertyChangeEvent> received = recordPces(s);

        // Identical re-sets: equality check in each setter skips before
        // the PCE can fire. Foreground/background/font round-trip through
        // CSS; for the common lossless cases (no alpha, generic families)
        // Objects.equals holds and the no-op is honoured.
        s.setName("foo");
        s.setForeground(Color.RED);
        s.setBackground(Color.BLUE);
        s.setFont(new Font("SansSerif", Font.PLAIN, 12));

        assertEquals(0, received.size());
    }

    @Test
    @DisplayName("name-filtered listener receives only the matching property")
    void nameFilteredListenerIsSelective() {
        final TestSurrogate s = new TestSurrogate();
        final List<PropertyChangeEvent> received = new ArrayList<>();
        s.addPropertyChangeListener("foreground", received::add);

        s.setName("ignored");
        s.setForeground(Color.RED);
        s.setEnabled(false);

        assertEquals(1, received.size());
        assertEquals("foreground", received.get(0).getPropertyName());
    }

    // --- SComponentListener (eager wire to attach/detach) -----------

    @Test
    @DisplayName("addComponentListener wires attach and detach to fire shown_hidden")
    void componentListenerWiresAttachDetach() {
        final TestSurrogate s = new TestSurrogate();
        final List<SComponentEvent> shown = new ArrayList<>();
        final List<SComponentEvent> hidden = new ArrayList<>();
        s.addComponentListener(new SComponentAdapter() {
            @Override
            public void componentShown(SComponentEvent e) {
                shown.add(e);
            }

            @Override
            public void componentHidden(SComponentEvent e) {
                hidden.add(e);
            }
        });

        UI.getCurrent().add(s);
        assertEquals(1, shown.size());
        assertSame(s, shown.get(0).getComponent());

        UI.getCurrent().remove(s);
        assertEquals(1, hidden.size());
    }

    @Test
    @DisplayName("removeComponentListener tears down attach and detach subscriptions")
    void removeComponentListenerTearsDown() {
        final TestSurrogate s = new TestSurrogate();
        final List<SComponentEvent> shown = new ArrayList<>();
        final SComponentAdapter listener = new SComponentAdapter() {
            @Override
            public void componentShown(SComponentEvent e) {
                shown.add(e);
            }
        };
        s.addComponentListener(listener);
        s.removeComponentListener(listener);
        UI.getCurrent().add(s);
        assertEquals(0, shown.size());
    }

    // --- SContainerListener (Bucket B onNoop — no Vaadin analog; SD_listeners_without_analog) -

    @Test
    @DisplayName("addContainerListener is onNoop — no storage, no WARN, empty listener array")
    void containerListenerIsNoop() {
        final TestSurrogate s = new TestSurrogate();
        final List<SContainerEvent> fired = new ArrayList<>();
        s.addContainerListener(new SContainerAdapter() {
            @Override
            public void componentAdded(SContainerEvent e) {
                fired.add(e);
            }
        });

        // Empty array — onNoop doesn't store. Honest about the fact that
        // the listener would never fire even if we did.
        assertEquals(0, s.getContainerListeners().length);
        // Bucket B doesn't feed warnHook.
        assertEquals(0, capturedWarns.size());
        assertEquals(0, fired.size());
    }

    // --- LayoutStore (Container only, inert) -------------------------

    @Test
    @DisplayName("setLayout round-trips via LayoutStore")
    void setLayoutRoundTrips() {
        final TestSurrogate s = new TestSurrogate();
        final FlowLayout flow = new FlowLayout();
        s.setLayout(flow);
        assertSame(flow, s.getLayout());
    }

    // --- setLayout drives CSS on a Div host --------------------------

    @Test
    @DisplayName("setLayout FlowLayout writes flex CSS to a Div container")
    void flowLayoutWritesFlexCss() {
        final DivContainerSurrogate s = new DivContainerSurrogate();
        s.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 10));

        final Style style = s.getElement().getStyle();
        assertEquals("flex", style.get("display"));
        assertEquals("wrap", style.get("flex-wrap"));
        assertEquals("center", style.get("justify-content"));
        // JDK FlowLayout vertically centers row components — flex
        // default (stretch) would top-align Vaadin text-field inputs
        // while leaving JLabels centered when a tall sibling (multi-row
        // JTextArea) sets the row height.
        assertEquals("center", style.get("align-items"));
        assertEquals("10px 5px", style.get("gap"));
    }

    @Test
    @DisplayName("setLayout BorderLayout writes grid CSS to a Div container")
    void borderLayoutWritesGridCss() {
        final DivContainerSurrogate s = new DivContainerSurrogate();
        s.setLayout(new BorderLayout(8, 4));

        final Style style = s.getElement().getStyle();
        assertEquals("grid", style.get("display"));
        assertEquals("auto minmax(0, 1fr) auto", style.get("grid-template-columns"));
        assertEquals("auto minmax(0, 1fr) auto", style.get("grid-template-rows"));
        assertEquals("4px 8px", style.get("gap"));
    }

    @Test
    @DisplayName("setLayout on non-Div container logs onUnsupportedPeerShape but doesn't throw")
    void setLayoutOnNonDivWarns() {
        final List<String> warns = new ArrayList<>();
        SHelper.warnHook = warns::add;
        try {
            final TestSurrogate s = new TestSurrogate();  // Button-backed, <vaadin-button>
            s.setLayout(new FlowLayout());
            assertEquals(1, warns.size());
            assertTrue(warns.get(0).contains("Unsupported peer shape"));
        } finally {
            SHelper.warnHook = msg -> { /* noop */ };
        }
    }

    @Test
    @DisplayName("setLayout custom LayoutManager stores but doesn't dispatch CSS")
    void customLayoutStoresWithoutCss() {
        final DivContainerSurrogate s = new DivContainerSurrogate();
        final LayoutManager custom = customLayout();
        s.setLayout(custom);
        assertSame(custom, s.getLayout());
        // No display CSS written for unrecognised layout managers.
        assertNull(s.getElement().getStyle().get("display"));
    }

    @Test
    @DisplayName("setLayout clears the outgoing manager's CSS")
    void setLayoutClearsOutgoingCss() {
        final DivContainerSurrogate s = new DivContainerSurrogate();
        s.setLayout(new BorderLayout(8, 4));
        assertEquals("grid", s.getElement().getStyle().get("display"));

        // A custom manager dispatches nothing, so without the reset the host
        // would keep BorderLayout's grid templates while children are laid out
        // by pixel code that knows nothing about them.
        s.setLayout(customLayout());
        final Style style = s.getElement().getStyle();
        assertNull(style.get("display"));
        assertNull(style.get("grid-template-columns"));
        assertNull(style.get("gap"));
    }

    @Test
    @DisplayName("setLayout null clears the store and the CSS")
    void setLayoutNullClearsAll() {
        final DivContainerSurrogate s = new DivContainerSurrogate();
        s.setLayout(new FlowLayout());
        assertEquals("flex", s.getElement().getStyle().get("display"));

        s.setLayout(null);
        assertNull(s.getLayout());
        assertNull(s.getElement().getStyle().get("display"));
        assertNull(s.getElement().getStyle().get("flex-wrap"));
    }

    // --- Tooltip / focus delegation ----------------------------------

    @Test
    @DisplayName("setToolTipText delegates to HasTooltip")
    void toolTipTextDelegates() {
        final TestSurrogate s = new TestSurrogate();
        s.setToolTipText("Click me");
        assertEquals("Click me", s.getTooltip().getText());
        assertEquals("Click me", s.getToolTipText());
    }

    @Test
    @DisplayName("requestFocus delegates to Focusable")
    void requestFocusDelegates() {
        final TestSurrogate s = new TestSurrogate();
        s.requestFocus();
        s.requestFocusInWindow();
    }

    // --- onUnimplemented stubs produce expected log shape ------------

    @Test
    @DisplayName("setLocation is silent (Bucket B)")
    void setLocationIsSilent() {
        // location is Bucket B onNoop — no Vaadin counterpart for absolute
        // pixel positioning at this layer (R_layouts_close_enough: pixel-accurate layout is
        // out of scope permanently) and nothing upstream-blocked to wait on.
        final TestSurrogate s = new TestSurrogate();
        s.setLocation(1, 2);
        s.setLocation(null);
        assertEquals(0, s.getX());
        assertEquals(0, s.getY());
        assertEquals(new Point(0, 0), s.getLocation());
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("addMouseListener wires ClickNotifier silently when peer supports it")
    void mouseListenerWiresSilently() {
        // TestSurrogate extends Button, which IS a ClickNotifier — so
        // mouse-listener registration wires silently. mouseClicked dispatch
        // is covered in dedicated tests; this one anchors the no-WARN claim.
        final TestSurrogate s = new TestSurrogate();
        s.addMouseListener(new SMouseAdapter() { });
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("null arg renders as the literal string null in the log")
    void nullArgRendersAsNull() {
        // Picks an onUnimplemented method that still WARNs to anchor the
        // null-formatting contract — addMouseWheelListener is a Vaadin-gap
        // stub that won't graduate until a custom DOM listener stack lands.
        final TestSurrogate s = new TestSurrogate();
        s.addMouseWheelListener(null);
        assertEquals(1, capturedWarns.size());
        assertEquals("Unimplemented TestSurrogate.addMouseWheelListener(null)", capturedWarns.get(0));
    }

    @Test
    @DisplayName("state holders are lazy and per-instance")
    void stateHoldersAreLazyPerInstance() {
        final TestSurrogate a = new TestSurrogate();
        final TestSurrogate b = new TestSurrogate();

        assertNull(ComponentUtil.getData(a, NameStore.class));

        a.setName("a");
        assertNull(ComponentUtil.getData(b, NameStore.class));

        b.setName("b");
        assertEquals("a", a.getName());
        assertEquals("b", b.getName());
    }

    // --- Bucket A: size / bounds via HasSize -------------------------

    @Test
    @DisplayName("setSize writes Npx to HasSize")
    void setSizeWritesPixels() {
        final TestSurrogate s = new TestSurrogate();
        s.setSize(120, 40);
        assertEquals("120px", s.getWidth());
        assertEquals("40px", s.getHeight());
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("getSize reads HasSize back in pixels")
    void getSizeReadsPixels() {
        final TestSurrogate s = new TestSurrogate();
        s.setSize(200, 80);
        final Dimension d = s.getSize();
        assertEquals(200, d.width);
        assertEquals(80, d.height);
    }

    @Test
    @DisplayName("getSize returns 1 when width or height is null")
    void getSizeUnsetReturnsOne() {
        final Dimension d = new TestSurrogate().getSize();
        assertEquals(1, d.width);
        assertEquals(1, d.height);
    }

    @Test
    @DisplayName("getSize returns 1 for non-pixel units")
    void getSizeNonPixelReturnsOne() {
        final TestSurrogate s = new TestSurrogate();
        s.setWidth("100%");
        s.setHeight("50%");
        final Dimension d = s.getSize();
        assertEquals(1, d.width);
        assertEquals(1, d.height);
    }

    @Test
    @DisplayName("setSize(Dimension) round-trips in pixels")
    void setSizeDimensionRoundTrips() {
        final TestSurrogate s = new TestSurrogate();
        s.setSize(new Dimension(64, 32));
        assertEquals("64px", s.getWidth());
        assertEquals("32px", s.getHeight());
        assertEquals(new Dimension(64, 32), s.getSize());
    }

    @Test
    @DisplayName("setSize(null Dimension) is onNoop not onUnimplemented")
    void setSizeNullIsNoop() {
        final TestSurrogate s = new TestSurrogate();
        s.setSize(null);
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("setBounds honours size and treats x,y as onNoop")
    void setBoundsHonoursSize() {
        final TestSurrogate s = new TestSurrogate();
        s.setBounds(10, 20, 300, 150);
        assertEquals("300px", s.getWidth());
        assertEquals("150px", s.getHeight());
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("setBounds(Rectangle) routes through setBounds(x,y,w,h)")
    void setBoundsRectangleRoutes() {
        final TestSurrogate s = new TestSurrogate();
        s.setBounds(new Rectangle(5, 6, 700, 400));
        assertEquals("700px", s.getWidth());
        assertEquals("400px", s.getHeight());
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("setBounds(null Rectangle) is onNoop")
    void setBoundsNullIsNoop() {
        final TestSurrogate s = new TestSurrogate();
        s.setBounds(null);
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("getBounds returns Rectangle at 0,0 with current size")
    void getBoundsAtOrigin() {
        final TestSurrogate s = new TestSurrogate();
        s.setSize(90, 45);
        final Rectangle r = s.getBounds();
        assertEquals(0, r.x);
        assertEquals(0, r.y);
        assertEquals(90, r.width);
        assertEquals(45, r.height);
    }

    @Test
    @DisplayName("preferred min max all read the same HasSize dimension (last write wins)")
    void preferredMinMaxShareOneDimension() {
        final TestSurrogate s = new TestSurrogate();
        s.setPreferredSize(new Dimension(10, 10));
        s.setMinimumSize(new Dimension(20, 20));
        s.setMaximumSize(new Dimension(30, 30));
        assertEquals(new Dimension(30, 30), s.getSize());
        assertEquals(new Dimension(30, 30), s.getPreferredSize());
        assertEquals(new Dimension(30, 30), s.getMinimumSize());
        assertEquals(new Dimension(30, 30), s.getMaximumSize());
    }

    // --- Bucket A: cursor round-trip via CSS -------------------------

    @Test
    @DisplayName("setCursor writes the CSS keyword")
    void setCursorWritesKeyword() {
        final TestSurrogate s = new TestSurrogate();
        s.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        assertEquals("pointer", s.getElement().getStyle().get("cursor"));
    }

    @Test
    @DisplayName("getCursor reconstructs the predefined Cursor from CSS")
    void getCursorReconstructs() {
        final TestSurrogate s = new TestSurrogate();
        s.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        assertEquals(Cursor.CROSSHAIR_CURSOR, s.getCursor().getType());
    }

    @Test
    @DisplayName("getCursor returns default when cursor CSS is missing")
    void getCursorDefaultsWhenUnset() {
        assertEquals(Cursor.DEFAULT_CURSOR, new TestSurrogate().getCursor().getType());
    }

    @Test
    @DisplayName("setCursor null removes the CSS property")
    void setCursorNullRemovesCss() {
        final TestSurrogate s = new TestSurrogate();
        s.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        s.setCursor(null);
        assertNull(s.getElement().getStyle().get("cursor"));
        assertEquals(Cursor.DEFAULT_CURSOR, s.getCursor().getType());
    }

    @Test
    @DisplayName("setCursor with unknown type WARNs via onUnimplemented")
    void setCursorUnknownWarns() {
        final TestSurrogate s = new TestSurrogate();
        s.setCursor(new Cursor("custom") { });
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).startsWith("Unimplemented TestSurrogate.setCursor("));
        assertNull(s.getElement().getStyle().get("cursor"));
    }

    // --- Bucket A: isShowing -----------------------------------------

    @Test
    @DisplayName("isShowing is true when component is attached to a UI")
    void isShowingWhenAttached() {
        final TestSurrogate s = new TestSurrogate();
        UI.getCurrent().add(s);
        assertTrue(s.isShowing());
    }

    @Test
    @DisplayName("isShowing is false when component is detached")
    void isShowingFalseWhenDetached() {
        assertFalse(new TestSurrogate().isShowing());
    }

    // --- Bucket B: deliberate no-ops must not emit WARN --------------

    @Test
    @DisplayName("Bucket B paint family emits no WARN")
    void paintFamilyIsSilent() {
        final TestSurrogate s = new TestSurrogate();
        s.repaint();
        s.repaint(100L);
        s.repaint(1, 2, 3, 4);
        s.repaint(100L, 1, 2, 3, 4);
        s.repaint(new Rectangle(0, 0, 1, 1));
        s.revalidate();
        s.invalidate();
        s.validate();
        s.doLayout();
        s.paint(null);
        s.update(null);
        s.paintAll(null);
        s.print(null);
        s.printAll(null);
        assertNull(s.getGraphics());
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("Bucket B peer lifecycle emits no WARN")
    void peerLifecycleIsSilent() {
        final TestSurrogate s = new TestSurrogate();
        s.addNotify();
        s.removeNotify();
        assertTrue(s.isDisplayable());
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("Container-only paint dispatch emits no WARN")
    void containerPaintDispatchIsSilent() {
        final TestSurrogate s = new TestSurrogate();
        s.paintComponents(null);
        s.printComponents(null);
        assertEquals(0, capturedWarns.size());
    }

    // --- SFocusListener (wired eagerly to FocusNotifier/BlurNotifier) -

    @Test
    @DisplayName("addFocusListener wires peer focus_blur to fire SFocusEvent")
    void focusListenerWires() {
        final TestSurrogate s = new TestSurrogate();
        s.addFocusListener(new SFocusAdapter() {
            @Override
            public void focusGained(SFocusEvent e) {
            }

            @Override
            public void focusLost(SFocusEvent e) {
            }
        });
        assertEquals(1, s.getFocusListeners().length);
        // Registration wired without WARN — Button is a FocusNotifier.
        assertEquals(0, capturedWarns.size());
    }

    @Test
    @DisplayName("removeFocusListener drops the listener")
    void removeFocusListenerDrops() {
        final TestSurrogate s = new TestSurrogate();
        final SFocusAdapter listener = new SFocusAdapter() { };
        s.addFocusListener(listener);
        s.removeFocusListener(listener);
        assertEquals(0, s.getFocusListeners().length);
    }

    // --- SHierarchyListener (wired eagerly to attach/detach) --------

    @Test
    @DisplayName("addHierarchyListener fires on attach and detach")
    void hierarchyListenerFiresOnAttachDetach() {
        final TestSurrogate s = new TestSurrogate();
        final List<SHierarchyEvent> events = new ArrayList<>();
        s.addHierarchyListener((SHierarchyListener) events::add);
        assertEquals(1, s.getHierarchyListeners().length);
        assertEquals(0, capturedWarns.size());

        UI.getCurrent().add(s);
        assertEquals(1, events.size());
        assertTrue((events.get(0).getChangeFlags() & SHierarchyEvent.SHOWING_CHANGED) != 0L);

        UI.getCurrent().remove(s);
        assertEquals(2, events.size());
    }

    @Test
    @DisplayName("addHierarchyBoundsListener is onNoop — empty listener array, no WARN (SD_listeners_without_analog)")
    void hierarchyBoundsListenerIsNoop() {
        final TestSurrogate s = new TestSurrogate();
        final List<SHierarchyEvent> fired = new ArrayList<>();
        s.addHierarchyBoundsListener(new SHierarchyBoundsAdapter() {
            @Override
            public void ancestorMoved(SHierarchyEvent e) {
                fired.add(e);
            }

            @Override
            public void ancestorResized(SHierarchyEvent e) {
                fired.add(e);
            }
        });
        // Bucket B onNoop: no storage, no round-trip. Matches the
        // runtime reality — this listener has no Vaadin event to fire on.
        assertEquals(0, s.getHierarchyBoundsListeners().length);
        assertEquals(0, capturedWarns.size());
        assertEquals(0, fired.size());
    }

    // --- Bucket C: listener families without Vaadin analog ----------

    @Test
    @DisplayName("addKeyListener WARNs on non-KeyNotifier peer (SD_key_events skip-and-warn)")
    void keyListenerWarnsOnNonKeyNotifier() {
        final TestSurrogate s = new TestSurrogate();  // extends Button — not a KeyNotifier
        s.addKeyListener(new SKeyAdapter() { });
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0)
                .startsWith("Unimplemented TestSurrogate.addKeyListener/non-KeyNotifier-peer("));
    }

    // --- Mouse-listener wire -----------------------------------------
    //
    // ClickNotifier-shaped peers route MOUSE_CLICKED to SMouseListener.
    // Other mouse-family methods (motion, wheel, press/release/enter/exit
    // outside of click) stay drop-and-WARN — Vaadin has no server-side
    // event source for those without a custom DOM listener stack.

    @Test
    @DisplayName("addMouseListener registers silently on ClickNotifier-shaped peer")
    void mouseListenerRegistersOnClickNotifier() {
        final TestSurrogate s = new TestSurrogate();  // Button, ClickNotifier
        s.addMouseListener(new SMouseAdapter() { });
        assertEquals(0, capturedWarns.size());
        assertEquals(1, s.getMouseListeners().length);
    }

    @Test
    @DisplayName("Vaadin click fires mouseClicked with the surrogate as source")
    void vaadinClickFiresMouseClicked() {
        final TestSurrogate s = new TestSurrogate();
        final List<SMouseEvent> received = recordClicks(s);

        ComponentUtil.fireEvent(s,
                new ClickEvent<>(s, true, 11, 22, 33, 44, 1, 0, false, false, false, false));

        assertEquals(1, received.size());
        final SMouseEvent e = received.get(0);
        assertEquals(SMouseEvent.MOUSE_CLICKED, e.getID());
        assertSame(s, e.getSource());
        assertEquals(33, e.getX());
        assertEquals(44, e.getY());
        assertEquals(11, e.getXOnScreen());
        assertEquals(22, e.getYOnScreen());
        assertEquals(1, e.getClickCount());
        assertEquals(SMouseEvent.BUTTON1, e.getButton());
    }

    @Test
    @DisplayName("modifier flags project to JDK DOWN_MASK bits")
    void modifierFlagsProject() {
        final TestSurrogate s = new TestSurrogate();
        final List<SMouseEvent> received = recordClicks(s);

        ComponentUtil.fireEvent(s, new ClickEvent<>(s, true, 0, 0, 0, 0, 1, 0,
                /*ctrl*/ true, /*shift*/ true, /*alt*/ false, /*meta*/ false));

        assertEquals(1, received.size());
        final SMouseEvent e = received.get(0);
        assertTrue(e.isControlDown());
        assertTrue(e.isShiftDown());
        assertFalse(e.isAltDown());
        assertFalse(e.isMetaDown());
    }

    @Test
    @DisplayName("Vaadin button index maps to JDK BUTTON1 BUTTON2 BUTTON3 NOBUTTON")
    void buttonIndexMaps() {
        final Map<Integer, Integer> captures = new HashMap<>();  // vaadinButton → mappedJdkButton
        for (int vaadinButton : List.of(0, 1, 2, -1)) {
            final int index = vaadinButton;
            final TestSurrogate s = new TestSurrogate();
            s.addMouseListener(new SMouseAdapter() {
                @Override
                public void mouseClicked(SMouseEvent e) {
                    captures.put(index, e.getButton());
                }
            });
            ComponentUtil.fireEvent(s, new ClickEvent<>(s, true, 0, 0, 0, 0, 1, index,
                    false, false, false, false));
        }
        assertEquals(SMouseEvent.BUTTON1, captures.get(0));
        assertEquals(SMouseEvent.BUTTON2, captures.get(1));
        assertEquals(SMouseEvent.BUTTON3, captures.get(2));
        assertEquals(SMouseEvent.NOBUTTON, captures.get(-1));
    }

    @Test
    @DisplayName("removeMouseListener tears down the Vaadin subscription")
    void removeMouseListenerTearsDown() {
        final TestSurrogate s = new TestSurrogate();
        final Counter hits = new Counter();
        final SMouseAdapter l = new SMouseAdapter() {
            @Override
            public void mouseClicked(SMouseEvent e) {
                hits.inc();
            }
        };
        s.addMouseListener(l);
        s.removeMouseListener(l);

        ComponentUtil.fireEvent(s, new ClickEvent<>(s));
        hits.assertEquals(0);
        assertEquals(0, s.getMouseListeners().length);
    }

    @Test
    @DisplayName("only mouseClicked fires — pressed released entered exited stay silent")
    void onlyMouseClickedFires() {
        final TestSurrogate s = new TestSurrogate();
        final List<String> calls = new ArrayList<>();
        s.addMouseListener(new SMouseListener() {
            @Override
            public void mouseClicked(SMouseEvent e) {
                calls.add("clicked");
            }

            @Override
            public void mousePressed(SMouseEvent e) {
                calls.add("pressed");
            }

            @Override
            public void mouseReleased(SMouseEvent e) {
                calls.add("released");
            }

            @Override
            public void mouseEntered(SMouseEvent e) {
                calls.add("entered");
            }

            @Override
            public void mouseExited(SMouseEvent e) {
                calls.add("exited");
            }
        });

        ComponentUtil.fireEvent(s, new ClickEvent<>(s));

        assertEquals(List.of("clicked"), calls);
    }

    @Test
    @DisplayName("addMouseListener WARNs on a non-ClickNotifier peer")
    void mouseListenerWarnsOnNonClickNotifier() {
        // Component (the abstract base) is not a ClickNotifier — this
        // surrogate exercises the WARN-and-skip branch.
        final NonClickSurrogate s = new NonClickSurrogate();
        s.addMouseListener(new SMouseAdapter() { });
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0)
                .startsWith("Unimplemented NonClickSurrogate.addMouseListener/non-ClickNotifier-peer("));
    }

    @Test
    @DisplayName("addMouseMotionListener still WARNs (Bucket C, no Vaadin counterpart)")
    void mouseMotionListenerWarns() {
        final TestSurrogate s = new TestSurrogate();
        s.addMouseMotionListener(new MouseMotionAdapter() { });
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).startsWith("Unimplemented TestSurrogate.addMouseMotionListener("));
    }

    @Test
    @DisplayName("addMouseWheelListener still WARNs (Bucket C, no Vaadin counterpart)")
    void mouseWheelListenerWarns() {
        final TestSurrogate s = new TestSurrogate();
        s.addMouseWheelListener((MouseWheelListener) e -> { });
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).startsWith("Unimplemented TestSurrogate.addMouseWheelListener("));
    }

    @Test
    @DisplayName("addInputMethodListener still WARNs (Bucket C)")
    void inputMethodListenerWarns() {
        final TestSurrogate s = new TestSurrogate();
        s.addInputMethodListener(new InputMethodListener() {
            @Override
            public void inputMethodTextChanged(InputMethodEvent event) {
            }

            @Override
            public void caretPositionChanged(InputMethodEvent event) {
            }
        });
        assertEquals(1, capturedWarns.size());
        assertTrue(capturedWarns.get(0).startsWith("Unimplemented TestSurrogate.addInputMethodListener("));
    }
}
