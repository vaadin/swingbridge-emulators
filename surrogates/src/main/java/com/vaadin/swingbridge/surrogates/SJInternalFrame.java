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

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ModalityMode;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.swingbridge.surrogates.swing.event.SInternalFrameEvent;
import com.vaadin.swingbridge.surrogates.swing.event.SInternalFrameListener;

import javax.swing.event.EventListenerList;
import java.util.Collection;

/**
 * Surrogate for {@link javax.swing.JInternalFrame} (SD_sjinternalframe) — the peer for
 * {@code :emulators} {@code JInternalFrame}. An internal frame renders as a
 * <em>decorated</em> non-modal Vaadin {@link com.vaadin.flow.component.dialog.Dialog}
 * overlay: it is the inverse of {@link SJWindow}, which strips the Dialog
 * chrome. Frames escape the desktop-pane's bounds and stack in Vaadin's
 * overlay order — reusing Vaadin's overlay primitive sidesteps the
 * D_glasspane_structural/SD_sjframe structural layered-pane deferral (D_internal_frames).
 *
 * <h2>Ctor delta over {@link SWindow} (SD_internalframe_chrome)</h2>
 *
 * <ul>
 *   <li><b>Keeps chrome</b> — {@code setHeaderTitle} for the title, a
 *       header close-X icon (SD_internalframe_fire_seams), {@code setDraggable(true)} +
 *       {@code setResizable(true)}: the title-bar drag and resize grips
 *       <em>are</em> JInternalFrame's drag / {@code resizable} affordances,
 *       which Vaadin Dialog draws for free.</li>
 *   <li><b>Modeless</b> — {@link ModalityMode#MODELESS}; an internal frame
 *       never blocks the page.</li>
 *   <li><b>Closes only via its close-X</b> — ESC / outside-click dismissal
 *       disarmed, matching Swing (a JInternalFrame doesn't close on ESC).</li>
 * </ul>
 *
 * <h2>Lifecycle events (SD_internalframe_fire_seams)</h2>
 *
 * The three {@link SWindow} lifecycle fire seams are overridden to fire
 * {@link SInternalFrameEvent} instead of window events — OPENED on first
 * show, CLOSING on the close-X (and any peer close), CLOSED on dispose.
 * ACTIVATED / DEACTIVATED fire from {@link #setSelected(boolean)} (SD_internalframe_activation).
 * ICONIFIED / DEICONIFIED never fire here — minimize is emulator-only
 * (SD_no_surrogate_iconify). Server-side-fire-only (SD_listeners_without_analog shape), like the Window families.
 */
public class SJInternalFrame extends SWindow {

    /**
     * Shared RootPaneContainer engine (fourth consumer, SD_scaffold_fourth_consumer). Field
     * initializer runs after the SWindow ctor chain; the add/remove
     * overrides null-guard for any super-ctor-time add landing first.
     */
    private final RootPaneScaffold scaffold =
            new RootPaneScaffold(this, this::createRootPane);

    /** Storage for {@link SInternalFrameListener} — server-side-fire-only. */
    private final EventListenerList listenerList =
            new EventListenerList();

    private String title = "";
    private boolean selected;
    private boolean maximum;

    // Header title-bar controls (BasicInternalFrameTitlePane analog). Each is
    // shown/hidden by the matching flag (SD_internalframe_chrome); the close-X routes to the
    // internal-frame close seam, iconify/maximize route to emulator-provided
    // handlers (which honour the vetoable JInternalFrame properties). Pure-
    // surrogate users with no handler installed get a sensible fallback.
    private Icon iconifyIcon;
    private Icon maximizeIcon;
    private Icon closeIcon;
    private Runnable iconifyHandler;
    private Runnable maximizeHandler;

    // Pre-maximize geometry, captured on setMaximum(true) so setMaximum(false)
    // can restore it. Null means "was at the centered default".
    private String preMaxWidth, preMaxHeight, preMaxTop, preMaxLeft;

    public SJInternalFrame() {
        this("");
    }

    public SJInternalFrame(String title) {
        super(null);
        if (title != null && !title.isEmpty()) {
            this.title = title;
            setHeaderTitle(title);
        }
        installHeaderButtons();
        setDraggable(true);
        setResizable(true);
        setModality(ModalityMode.MODELESS);
        setCloseOnEsc(false);
        setCloseOnOutsideClick(false);
    }

    // ---- Header title-bar controls (BasicInternalFrameTitlePane analog) --

    /**
     * Install the iconify / maximize / close icons into the Dialog header,
     * mirroring Swing's title-bar buttons. Order is iconify, maximize, close
     * (close rightmost). Each is an {@link Icon} rather than a Button so it
     * doesn't pollute Karibu {@code _get<Button>()} queries. Visibility is
     * gated per-control (default: close shown, iconify/maximize hidden — the
     * emulator flips them from the JInternalFrame ctor flags via
     * {@link #setIconifiable}/{@link #setMaximizable}/{@link #setClosable}).
     *
     * <p>Close routes to {@link #fireClosingEvent()} ({@code INTERNAL_FRAME_CLOSING},
     * per SD_internalframe_fire_seams). Iconify / maximize route to the emulator-supplied handlers
     * (which honour the vetoable {@code icon} / {@code maximum} properties);
     * a pure-surrogate user with no handler falls back to hiding the overlay
     * / toggling {@link #setMaximum} directly.
     */
    private void installHeaderButtons() {
        iconifyIcon = headerIcon(VaadinIcon.MINUS, "Minimize");
        iconifyIcon.setVisible(false);
        iconifyIcon.addClickListener(e -> SHelper.callSwing(() -> {
            if (iconifyHandler != null) iconifyHandler.run();
            else setVisible(false);
        }));

        maximizeIcon = headerIcon(VaadinIcon.EXPAND_SQUARE, "Maximize");
        maximizeIcon.setVisible(false);
        maximizeIcon.addClickListener(e -> SHelper.callSwing(() -> {
            if (maximizeHandler != null) maximizeHandler.run();
            else setMaximum(!maximum);
        }));

        closeIcon = headerIcon(VaadinIcon.CLOSE, "Close");
        closeIcon.addClickListener(e -> SHelper.callSwing(this::fireClosingEvent));

        getHeader().add(iconifyIcon, maximizeIcon, closeIcon);
    }

    private static Icon headerIcon(VaadinIcon glyph, String label) {
        Icon icon = new Icon(glyph);
        icon.getElement().setAttribute("aria-label", label);
        icon.getElement().setAttribute("role", "button");
        icon.getElement().setAttribute("tabindex", "0");
        icon.getStyle().set("cursor", "pointer");
        return icon;
    }

    /** Show / hide the header close-X (JInternalFrame {@code closable}). */
    public void setClosable(boolean closable) {
        if (closeIcon == null) return;
        boolean old = closeIcon.isVisible();
        closeIcon.setVisible(closable);
        firePropertyChange("closable", old, closable);
    }

    /** Show / hide the header minimize button (JInternalFrame {@code iconifiable}). */
    public void setIconifiable(boolean iconifiable) {
        if (iconifyIcon == null) return;
        boolean old = iconifyIcon.isVisible();
        iconifyIcon.setVisible(iconifiable);
        // "iconable", not "iconifiable" — the JDK fires under the *field* name, not
        // the setter's (D_property_fanout_audit found the same trap on the emulator side).
        firePropertyChange("iconable", old, iconifiable);
    }

    /** Show / hide the header maximize button (JInternalFrame {@code maximizable}). */
    public void setMaximizable(boolean maximizable) {
        if (maximizeIcon == null) return;
        boolean old = maximizeIcon.isVisible();
        maximizeIcon.setVisible(maximizable);
        firePropertyChange("maximizable", old, maximizable);
    }

    /**
     * Install the action the header minimize button runs — the emulator wires
     * this to its vetoable {@code setIcon(true)} so a header-click honours the
     * JInternalFrame {@code icon} constrained property.
     */
    public void setIconifyHandler(Runnable r) {
        this.iconifyHandler = r;
    }

    /** Install the action the header maximize button runs (toggles {@code maximum}). */
    public void setMaximizeHandler(Runnable r) {
        this.maximizeHandler = r;
    }

    // ---- Title ----------------------------------------------------------

    /** Never-null. */
    public String getTitle() {
        return title;
    }

    /** Mirrors {@link com.vaadin.flow.component.dialog.Dialog#setHeaderTitle}; fires "title" PCE. */
    public void setTitle(String title) {
        String normalized = title != null ? title : "";
        String old = this.title;
        if (old.equals(normalized)) return;
        this.title = normalized;
        setHeaderTitle(normalized);
        firePropertyChange("title", old, normalized);
    }

    // ---- Selection → ACTIVATED / DEACTIVATED (SD_internalframe_activation) --------------------

    public boolean isSelected() {
        return selected;
    }

    /**
     * Select / deselect the frame. Fires {@code INTERNAL_FRAME_ACTIVATED}
     * / {@code DEACTIVATED} on change and a {@code "selected"} PCE. Precise
     * front-raise is Vaadin overlay order (R_layouts_close_enough) — no programmatic toFront.
     */
    public void setSelected(boolean selected) {
        if (this.selected == selected) return;
        this.selected = selected;
        firePropertyChange("selected", !selected, selected);
        fireInternalFrameEvent(selected
                ? SInternalFrameEvent.INTERNAL_FRAME_ACTIVATED
                : SInternalFrameEvent.INTERNAL_FRAME_DEACTIVATED);
    }

    // ---- Maximize → viewport-fill geometry (SD_internalframe_maximize) ----------------------

    public boolean isMaximum() {
        return maximum;
    }

    /**
     * Maximize / restore. Maximize drives the overlay to fill the viewport
     * (top/left 0, 100%/100%); restore returns to the captured pre-maximize
     * geometry, or the centered default if none. Vaadin Dialog has no
     * native maximize toggle, so this is surrogate-driven geometry. Bound
     * property only — no {@link SInternalFrameEvent} fires (matches JDK).
     */
    public void setMaximum(boolean maximum) {
        if (this.maximum == maximum) return;
        this.maximum = maximum;
        if (maximum) {
            preMaxWidth = getWidth();
            preMaxHeight = getHeight();
            preMaxTop = getTop();
            preMaxLeft = getLeft();
            setTop("0");
            setLeft("0");
            setWidth("100%");
            setHeight("100%");
        } else {
            setWidth(preMaxWidth);
            setHeight(preMaxHeight);
            if (preMaxTop == null && preMaxLeft == null) {
                centerOnScreen();
            } else {
                setTop(preMaxTop);
                setLeft(preMaxLeft);
            }
        }
        firePropertyChange("maximum", !maximum, maximum);
    }

    // ---- InternalFrameListener family (server-side-fire-only) -----------

    public void addInternalFrameListener(SInternalFrameListener l) {
        listenerList.add(SInternalFrameListener.class, l);
    }

    public void removeInternalFrameListener(SInternalFrameListener l) {
        listenerList.remove(SInternalFrameListener.class, l);
    }

    public SInternalFrameListener[] getInternalFrameListeners() {
        return listenerList.getListeners(SInternalFrameListener.class);
    }

    /** Dispatch one {@link SInternalFrameEvent} id to registered listeners. */
    public void fireInternalFrameEvent(int id) {
        SInternalFrameListener[] listeners = listenerList.getListeners(SInternalFrameListener.class);
        if (listeners.length == 0) return;
        SInternalFrameEvent e = new SInternalFrameEvent(this, id);
        for (SInternalFrameListener l : listeners) {
            switch (id) {
                case SInternalFrameEvent.INTERNAL_FRAME_OPENED      -> l.internalFrameOpened(e);
                case SInternalFrameEvent.INTERNAL_FRAME_CLOSING     -> l.internalFrameClosing(e);
                case SInternalFrameEvent.INTERNAL_FRAME_CLOSED      -> l.internalFrameClosed(e);
                case SInternalFrameEvent.INTERNAL_FRAME_ICONIFIED   -> l.internalFrameIconified(e);
                case SInternalFrameEvent.INTERNAL_FRAME_DEICONIFIED -> l.internalFrameDeiconified(e);
                case SInternalFrameEvent.INTERNAL_FRAME_ACTIVATED   -> l.internalFrameActivated(e);
                case SInternalFrameEvent.INTERNAL_FRAME_DEACTIVATED -> l.internalFrameDeactivated(e);
                default -> { /* out-of-range id, drop */ }
            }
        }
    }

    // ---- SWindow lifecycle fire seams → internal-frame events (SD_internalframe_fire_seams) ---

    @Override
    protected void fireOpenedEvent() {
        fireInternalFrameEvent(SInternalFrameEvent.INTERNAL_FRAME_OPENED);
    }

    @Override
    protected void fireClosingEvent() {
        fireInternalFrameEvent(SInternalFrameEvent.INTERNAL_FRAME_CLOSING);
    }

    @Override
    protected void fireClosedEvent() {
        fireInternalFrameEvent(SInternalFrameEvent.INTERNAL_FRAME_CLOSED);
    }

    // ---- Content pane (RootPaneContainer surface, mirrors SJWindow) -----

    public Div getContentPane() {
        return scaffold.getContentPane();
    }

    public void setContentPane(Div newPane) {
        Div old = scaffold.getContentPane();
        scaffold.setContentPane(newPane);
        firePropertyChange("contentPane", old, newPane);
    }

    // ---- add / remove routing (mirrors SJWindow) ------------------------

    @Override
    public void add(Collection<Component> components) {
        if (scaffold != null && scaffold.isChecking()) {
            scaffold.addToContent(components);
        } else {
            super.add(components);
        }
    }

    @Override
    public void addComponentAtIndex(int index, Component component) {
        if (scaffold != null && scaffold.isChecking()) {
            scaffold.addToContentAtIndex(index, component);
        } else {
            super.addComponentAtIndex(index, component);
        }
    }

    @Override
    public void remove(Component... components) {
        if (scaffold != null && scaffold.isChecking()) {
            scaffold.removeFromContent(components);
        } else {
            super.remove(components);
        }
    }

    @Override
    public void remove(Collection<Component> components) {
        if (scaffold != null && scaffold.isChecking()) {
            scaffold.removeFromContent(components);
        } else {
            super.remove(components);
        }
    }

    @Override
    public void removeAll() {
        if (scaffold != null && scaffold.isChecking()) {
            scaffold.removeAllFromContent();
        } else {
            super.removeAll();
        }
    }

    // ---- Root pane (mirrors SJWindow) -----------------------------------

    @Override
    public SJRootPane getRootPane() {
        return scaffold.getRootPane();
    }

    /**
     * Replaces the root pane, as the JDK's protected {@code setRootPane} does: the outgoing
     * one leaves this window and {@code root} becomes its one child, holding the content pane
     * from then on. Public so the emulator layer can hand down the peer of the root pane it
     * built itself, which is how one root pane serves both layers.
     *
     * @param root {@code null} leaves the window without a root pane, as the JDK allows
     */
    public void setRootPane(SJRootPane root) {
        scaffold.setRootPane(root);
    }

    /** Subclass hook matching JDK JInternalFrame's {@code createRootPane}. */
    protected SJRootPane createRootPane() {
        return new SJRootPane();
    }

    // ---- Layered / glass pane pass-throughs (mirrors SJWindow) ----------

    public Component getLayeredPane() {
        return getRootPane().getLayeredPane();
    }

    public void setLayeredPane(Component layered) {
        Component old = getRootPane().getLayeredPane();
        getRootPane().setLayeredPane(layered);
        firePropertyChange("layeredPane", old, layered);
    }

    public Component getGlassPane() {
        return getRootPane().getGlassPane();
    }

    public void setGlassPane(Component glass) {
        Component old = getRootPane().getGlassPane();
        getRootPane().setGlassPane(glass);
        firePropertyChange("glassPane", old, glass);
    }

    @Override
    public String paramString() {
        StringBuilder sb = new StringBuilder();
        if (!title.isEmpty()) sb.append("title=").append(title).append(',');
        sb.append("selected=").append(selected).append(',');
        sb.append("maximum=").append(maximum).append(',');
        sb.append(super.paramString());
        return sb.toString();
    }
}
