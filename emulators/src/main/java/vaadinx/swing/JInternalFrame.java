/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This file is derived from OpenJDK's javax.swing.JInternalFrame
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

import vaadinx.swing.event.InternalFrameEvent;
import vaadinx.swing.event.InternalFrameListener;

// Hand-finished emulator for javax.swing.JInternalFrame (D_internal_frames). A
// JInternalFrame is a JComponent (NOT a Window) in the JDK, so this
// extends vaadinx.swing.JComponent — but its peer is an SJInternalFrame,
// a Dialog-backed overlay (SD_sjinternalframe). The frame therefore renders as a
// decorated non-modal Vaadin Dialog: draggable/resizable/titled with a
// close-X, escaping the desktop-pane bounds and stacking in Vaadin's
// overlay order (R_layouts_close_enough). Reusing the overlay primitive sidesteps the
// D_glasspane_structural/SD_sjframe structural layered-pane deferral.
//
// R_leaf_peer_lockdown leaf lock-down: javax.swing.JInternalFrame is a public-hierarchy
// leaf, so the peer is hardcoded to com.vaadin.swingbridge.surrogates.SJInternalFrame; no
// protected (Component peer) ctor.
//
// Two-layer event bridge: the SJInternalFrame peer is the fire authority
// for lifecycle events (OPENED on first show, CLOSING on close-X, CLOSED
// on dispose, ACTIVATED/DEACTIVATED on selection). This emulator
// registers ONE SInternalFrameListener on the peer to relay those onto
// its own listenerList as vaadinx.swing.event.InternalFrameEvent with
// source=this, and applies the default-close-operation on CLOSING.
// ICONIFIED/DEICONIFIED are NOT bridged — minimize is emulator-only
// (D_internalframe_minimize): setIcon models the full state machine here and drives the peer
// overlay hidden/shown directly.

/** Emulator for {@link javax.swing.JInternalFrame}. R_leaf_peer_lockdown-locked peer is {@link com.vaadin.swingbridge.surrogates.SJInternalFrame}. */
public class JInternalFrame extends vaadinx.swing.JComponent
        implements javax.accessibility.Accessible, javax.swing.WindowConstants,
                   vaadinx.FieldReconciler.Reconcilable, vaadinx.swing.RootPaneContainer {

    /** D_field_write_reconcile repair hook — see {@link JSlider#reconcileFields()}. */
    @Override
    public final void reconcileFields() {
        if (!java.util.Objects.equals(title, pushedTitle)) {
            if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SJInternalFrame sif) {
                sif.setTitle(title);
            }
            pushedTitle = title;
            vaadinx.FieldReconciler.reportDirectWrite(this, "title", "setTitle");
        }
    }

    // The four close-operation constants are inherited from WindowConstants,
    // as in the JDK. EXIT_ON_CLOSE comes along with them but is not a valid
    // JInternalFrame value.

    // JDK constrained-property name constants (bound + vetoable).
    public static final String CONTENT_PANE_PROPERTY = "contentPane";
    public static final String MENU_BAR_PROPERTY     = "JMenuBar";
    public static final String TITLE_PROPERTY        = "title";
    public static final String LAYERED_PANE_PROPERTY = "layeredPane";
    public static final String ROOT_PANE_PROPERTY    = "rootPane";
    public static final String GLASS_PANE_PROPERTY   = "glassPane";
    public static final String FRAME_ICON_PROPERTY   = "frameIcon";
    public static final String IS_SELECTED_PROPERTY  = "selected";
    public static final String IS_CLOSED_PROPERTY    = "closed";
    public static final String IS_MAXIMUM_PROPERTY   = "maximum";
    public static final String IS_ICON_PROPERTY      = "icon";

    // ---- Content pane / root pane (mirrors JDialog) --------------------

    private vaadinx.awt.Container contentPane;
    protected JRootPane rootPane;
    protected boolean rootPaneCheckingEnabled;

    // ---- JInternalFrame state (R_swing_is_truth source of truth for the JDK API) ------

    // JInternalFrame's own default is DISPOSE_ON_CLOSE (unlike JFrame's
    // HIDE_ON_CLOSE).
    private int defaultCloseOperation = DISPOSE_ON_CLOSE;

    // A JInternalFrame is invisible until shown; JComponent defaults to
    // visible=true (and its field lives in vaadinx.awt, inaccessible here),
    // so we track visibility with our own field + override setVisible/isVisible.
    private boolean frameVisible = false;

    // JDK protected field names kept verbatim (D_instance_field_surface) — the
    // JDK really does pair a field `isClosed` with a method `isClosed()`.
    protected boolean isClosed;
    protected boolean isIcon;      // iconified (minimized) — emulator-only visual
    protected boolean isMaximum;
    protected boolean isSelected;

    // Title-bar affordance flags. Stored R_swing_is_truth; resizable drives the peer, the
    // rest are API round-trip (they gate which title-bar buttons a real L&F
    // would draw — we render the Dialog chrome regardless, an accepted R_best_effort_behaviour
    // divergence for closable/maximizable/iconifiable).
    protected boolean resizable;
    protected boolean closable;
    protected boolean maximizable;
    // The JDK spells this field `iconable` (a quirk its own accessors don't
    // share — they say isIconifiable/setIconifiable); reproduced rather than
    // tidied, per R_no_silent_improvements.
    protected boolean iconable;

    protected JDesktopIcon desktopIcon;
    protected vaadinx.swing.Icon frameIcon;

    // JDK protected field, Swing-side truth per D_field_write_reconcile (see JSlider for the
    // canonical commentary); write-throughs to the peer Dialog's header text.
    protected String title;

    // Last value pushed to the peer — reconcileFields()'s write-detection baseline.
    private String pushedTitle;

    // ---- Constructors (6 JDK signatures) --------------------------------

    public JInternalFrame() {
        this("", false, false, false, false);
    }

    public JInternalFrame(String title) {
        this(title, false, false, false, false);
    }

    public JInternalFrame(String title, boolean resizable) {
        this(title, resizable, false, false, false);
    }

    public JInternalFrame(String title, boolean resizable, boolean closable) {
        this(title, resizable, closable, false, false);
    }

    public JInternalFrame(String title, boolean resizable, boolean closable,
                          boolean maximizable) {
        this(title, resizable, closable, maximizable, false);
    }

    public JInternalFrame(String title, boolean resizable, boolean closable,
                          boolean maximizable, boolean iconifiable) {
        super(new com.vaadin.swingbridge.surrogates.SJInternalFrame(title));
        this.title = pushedTitle = title;
        this.resizable = resizable;
        this.closable = closable;
        this.maximizable = maximizable;
        this.iconable = iconifiable;
        this.desktopIcon = new JDesktopIcon(this);
        frameInit();
        vaadinx.FieldReconciler.register(this);
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SJInternalFrame sif) {
            // Drive the peer's native resize affordance + title-bar controls
            // from the ctor flags, and wire the header minimize / maximize
            // buttons to our vetoable setIcon / setMaximum (so a header click
            // honours the JInternalFrame constrained properties).
            sif.setResizable(resizable);
            sif.setClosable(closable);
            sif.setIconifiable(iconifiable);
            sif.setMaximizable(maximizable);
            sif.setIconifyHandler(this::iconifyFromHeader);
            sif.setMaximizeHandler(this::toggleMaximumFromHeader);
        }
    }

    /** Header minimize button → vetoable {@code setIcon(true)} (veto swallowed). */
    private void iconifyFromHeader() {
        try {
            setIcon(true);
        } catch (java.beans.PropertyVetoException vetoed) {
            // A listener refused the iconify — leave the frame shown.
        }
    }

    /** Header maximize button → vetoable {@code setMaximum} toggle (veto swallowed). */
    private void toggleMaximumFromHeader() {
        try {
            setMaximum(!isMaximum);
        } catch (java.beans.PropertyVetoException vetoed) {
            // A listener refused the maximize — leave the frame as-is.
        }
    }

    // ---- frameInit / contentPane (mirrors JDialog.dialogInit) -----------

    /**
     * Mirror of JDK {@code JInternalFrame} construction: plant the
     * emulator-side content pane with {@code rootPaneCheckingEnabled=false}
     * so {@code super.addImpl} falls through to {@code Container.addImpl},
     * then flip checking on and wire the peer→emulator event relay.
     */
    protected void frameInit() {
        contentPane = createContentPane();
        super.addImpl(contentPane, null, -1);
        setRootPaneCheckingEnabled(true);
        installPeerRelay();
    }

    protected vaadinx.awt.Container createContentPane() {
        return new vaadinx.awt.Container();
    }

    /**
     * Register the single {@link com.vaadin.swingbridge.surrogates.swing.event.SInternalFrameListener}
     * that relays the peer's lifecycle fires onto this emulator's own
     * {@link InternalFrameListener} list (source = this) and applies the
     * default-close-operation on CLOSING.
     */
    private void installPeerRelay() {
        if (!(getPeer() instanceof com.vaadin.swingbridge.surrogates.SJInternalFrame sif)) return;
        sif.addInternalFrameListener(new com.vaadin.swingbridge.surrogates.swing.event.SInternalFrameListener() {
            @Override public void internalFrameOpened(com.vaadin.swingbridge.surrogates.swing.event.SInternalFrameEvent e) {
                fireInternalFrameEvent(InternalFrameEvent.INTERNAL_FRAME_OPENED);
            }
            @Override public void internalFrameClosing(com.vaadin.swingbridge.surrogates.swing.event.SInternalFrameEvent e) {
                // No fire here: doDefaultCloseAction fires CLOSING itself, as
                // the JDK's does — see its javadoc (D_owed_events).
                doDefaultCloseAction();
            }
            @Override public void internalFrameClosed(com.vaadin.swingbridge.surrogates.swing.event.SInternalFrameEvent e) {
                fireInternalFrameEvent(InternalFrameEvent.INTERNAL_FRAME_CLOSED);
            }
            @Override public void internalFrameIconified(com.vaadin.swingbridge.surrogates.swing.event.SInternalFrameEvent e) {
                // Never fires — minimize is emulator-only (D_internalframe_minimize).
            }
            @Override public void internalFrameDeiconified(com.vaadin.swingbridge.surrogates.swing.event.SInternalFrameEvent e) {
                // Never fires — see internalFrameIconified.
            }
            @Override public void internalFrameActivated(com.vaadin.swingbridge.surrogates.swing.event.SInternalFrameEvent e) {
                fireInternalFrameEvent(InternalFrameEvent.INTERNAL_FRAME_ACTIVATED);
            }
            @Override public void internalFrameDeactivated(com.vaadin.swingbridge.surrogates.swing.event.SInternalFrameEvent e) {
                fireInternalFrameEvent(InternalFrameEvent.INTERNAL_FRAME_DEACTIVATED);
            }
        });
    }

    public vaadinx.awt.Container getContentPane() {
        return contentPane;
    }

    public void setContentPane(vaadinx.awt.Container newPane) {
        if (newPane == null) {
            throw new java.awt.IllegalComponentStateException("contentPane cannot be set to null");
        }
        if (newPane == this.contentPane) return;
        vaadinx.awt.Container old = this.contentPane;
        // Suspend the redirect through the accessors, not the field: the JDK's
        // own setRootPane brackets its re-parent with
        // isRootPaneCheckingEnabled() / setRootPaneCheckingEnabled(), so a
        // subclass that overrides either sees both halves (R_no_vaadin_in_api
        // limb 2, D_dead_hook_lint).
        boolean wasChecking = isRootPaneCheckingEnabled();
        setRootPaneCheckingEnabled(false);
        try {
            if (old != null) super.remove(old);
            super.addImpl(newPane, null, -1);
        } finally {
            setRootPaneCheckingEnabled(wasChecking);
        }
        this.contentPane = newPane;
        firePropertyChange(CONTENT_PANE_PROPERTY, old, newPane);
    }

    // ---- add/remove/setLayout redirect (mirrors JDialog) ----------------

    @Override
    protected void addImpl(vaadinx.awt.Component comp, Object constraints, int index) {
        if (isRootPaneCheckingEnabled()) {
            contentPane.add(comp, constraints, index);
        } else {
            super.addImpl(comp, constraints, index);
        }
    }

    @Override
    public void setLayout(vaadinx.awt.LayoutManager manager) {
        if (isRootPaneCheckingEnabled()) {
            contentPane.setLayout(manager);
        } else {
            super.setLayout(manager);
        }
    }

    @Override
    public void remove(vaadinx.awt.Component comp) {
        if (isRootPaneCheckingEnabled()) {
            contentPane.remove(comp);
        } else {
            super.remove(comp);
        }
    }

    /**
     * Companion to {@link #isRootPaneCheckingEnabled}: every internal write to
     * the flag goes through here, so a subclass override observes the suspend /
     * restore that brackets a content-pane swap as it does on the desktop.
     */
    protected void setRootPaneCheckingEnabled(boolean enabled) {
        this.rootPaneCheckingEnabled = enabled;
    }

    /**
     * Whether {@code add} / {@code remove} / {@code setLayout} redirect into the
     * content pane. The redirect consults this method rather than the field, so a
     * subclass override is honoured (R_no_vaadin_in_api second limb, D_r12_provenance).
     */
    protected boolean isRootPaneCheckingEnabled() {
        return rootPaneCheckingEnabled;
    }

    // ---- JRootPane (lazy, mirrors JDialog) ------------------------------

    public JRootPane getRootPane() {
        if (rootPane == null) {
            rootPane = createRootPane();
            rootPane.setContentPane(contentPane);
        }
        return rootPane;
    }

    /**
     * The JDK also re-parents the pane through {@code remove} / {@code add}
     * under a suspended root-pane check; that half is R_layouts_close_enough layout and stays out.
     * The {@code "rootPane"} fire is not (D_owed_events).
     */
    protected void setRootPane(JRootPane newRootPane) {
        JRootPane old = this.rootPane;
        this.rootPane = newRootPane;
        firePropertyChange(ROOT_PANE_PROPERTY, old, newRootPane);
    }

    protected JRootPane createRootPane() {
        // Share the SJRootPane surrogate with the peer so glass/layered pane
        // and default-button state unify across layers — same idiom as
        // JDialog.createRootPane.
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SJInternalFrame sif) {
            return new JRootPane(sif.getRootPane());
        }
        return new JRootPane();
    }

    public JLayeredPane getLayeredPane() {
        return getRootPane().getLayeredPane();
    }

    public void setLayeredPane(JLayeredPane layered) {
        JLayeredPane old = getLayeredPane();
        getRootPane().setLayeredPane(layered);
        firePropertyChange(LAYERED_PANE_PROPERTY, old, layered);
    }

    public vaadinx.awt.Component getGlassPane() {
        return getRootPane().getGlassPane();
    }

    public void setGlassPane(vaadinx.awt.Component glass) {
        vaadinx.awt.Component old = getGlassPane();
        getRootPane().setGlassPane(glass);
        firePropertyChange(GLASS_PANE_PROPERTY, old, glass);
    }

    // ---- Visibility (drive the Dialog overlay open/close) ---------------

    /**
     * A JInternalFrame's peer is a Dialog overlay, so visibility means
     * open/close, not display toggling. Drive the peer's
     * {@link com.vaadin.swingbridge.surrogates.SWindow#setVisible(boolean)} (which attaches +
     * opens the overlay and fires INTERNAL_FRAME_OPENED via the relay on
     * first show) and track our own field, since JComponent's
     * {@code visible} field lives in another package and defaults to true.
     */
    @Override
    public void setVisible(boolean b) {
        boolean old = this.frameVisible;
        if (old == b) return;
        this.frameVisible = b;
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SWindow sw) {
            // Live-UI hop, not withPeer: showing attaches the overlay, which needs a
            // current UI even while the peer is still detached.
            withPeerOnLiveUI(false, p -> sw.setVisible(b));
        }
        // No "visible" bound property — see Component.setVisible (R_decline_effect_only).
        // JInternalFrame's own bound properties are the JDK's IS_*_PROPERTY
        // constants ("closed", "selected", "maximum", "icon"), not this one.
        fireComponentEvent(b
                ? vaadinx.awt.event.ComponentEvent.COMPONENT_SHOWN
                : vaadinx.awt.event.ComponentEvent.COMPONENT_HIDDEN);
    }

    @Override
    public boolean isVisible() {
        return frameVisible;
    }

    /**
     * {@inheritDoc}
     *
     * <p>All three conjuncts are the JDK's, measured: a visible frame added
     * nowhere, and a visible frame on a desktop that is in no window, both
     * answer {@code false} on JDK 25.
     *
     * <p>AWT's inherited body cannot serve, for two independent reasons.
     * It reads {@code Component.visible}, which is package-private to
     * {@code vaadinx.awt}, so this class carries {@link #frameVisible}
     * instead and the inherited body would read a {@code true} nobody
     * wrote. And its parent walk has nothing to walk: per
     * D_jdesktoppane_holder the frame escapes to its own overlay rather
     * than becoming the desktop's child, so it is never
     * {@code displayable} and AWT's body would refuse <em>every</em>
     * {@link #setSelected}.
     */
    @Override
    public boolean isShowing() {
        JDesktopPane d = getDesktopPane();
        return frameVisible && d != null && d.isShowing();
    }

    /**
     * @return the desktop this frame was added to, or {@code null} if none. The
     *         JDK finds it by walking parents and falls back to searching from
     *         the {@link #getDesktopIcon() desktop icon}; neither works here
     *         (D_jdesktoppane_holder), so it is recorded on the way in instead.
     */
    public JDesktopPane getDesktopPane() {
        return desktop;
    }

    /** Stands in for the parent link {@link #getDesktopPane()} cannot walk. */
    JDesktopPane desktop;

    // ---- Geometry (drives the overlay, like Window; D_internal_frames/D_window_geometry shape) ------
    //
    // A JInternalFrame's peer is a Dialog overlay, so its bounds map 1:1
    // onto the overlay's top/left/width/height (no layout manager) — the
    // same window-level carve-out from R_layouts_close_enough's pixel-layout exclusion that
    // Window takes. Component's setBounds/setSize/setLocation WARN (R_layouts_close_enough); we
    // override to drive the peer and serve R_swing_is_truth shadows on read.

    private java.awt.Point location;
    private java.awt.Dimension size;

    @Override
    public void setLocation(int x, int y) {
        this.location = new java.awt.Point(x, y);
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SWindow sw) withPeer(p -> sw.setLocation(x, y));
    }

    @Override
    public void setLocation(java.awt.Point p) {
        setLocation(p.x, p.y);
    }

    @Override
    public void setSize(int width, int height) {
        this.size = new java.awt.Dimension(width, height);
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SWindow sw) withPeer(p -> sw.setSize(width, height));
    }

    @Override
    public void setSize(java.awt.Dimension d) {
        setSize(d.width, d.height);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        setLocation(x, y);
        setSize(width, height);
    }

    @Override
    public void setBounds(java.awt.Rectangle r) {
        setBounds(r.x, r.y, r.width, r.height);
    }

    @Override
    public java.awt.Point getLocation() {
        return location != null ? new java.awt.Point(location) : super.getLocation();
    }

    @Override
    public java.awt.Dimension getSize() {
        return size != null ? new java.awt.Dimension(size) : super.getSize();
    }

    // ---- Constrained (vetoable) properties ------------------------------

    public boolean isClosed() {
        return isClosed;
    }

    /**
     * Constrained property. Fires the vetoable {@code "closed"} change
     * first (a veto aborts with no state change), then closes the frame by
     * disposing the peer — which fires INTERNAL_FRAME_CLOSED via the relay.
     */
    public void setClosed(boolean b) throws java.beans.PropertyVetoException {
        if (isClosed == b) return;
        fireVetoableChange(IS_CLOSED_PROPERTY, isClosed, b);
        boolean old = isClosed;
        isClosed = b;
        if (b && getPeer() instanceof com.vaadin.swingbridge.surrogates.SWindow sw) {
            withPeer(p -> sw.dispose());
        }
        firePropertyChange(IS_CLOSED_PROPERTY, old, b);
    }

    public boolean isIcon() {
        return isIcon;
    }

    /**
     * Constrained property — iconify (minimize). Emulator-complete
     * (D_internalframe_minimize): honors the vetoable change, tracks state, fires
     * INTERNAL_FRAME_ICONIFIED / DEICONIFIED, and hides / restores the peer
     * overlay (via the programmatic {@code setVisible} channel, which does
     * not reset the OPENED latch, so restore doesn't refire OPENED). The
     * one thing the browser can't render is the clickable JDesktopIcon
     * restore affordance — a documented divergence, not a WARN; the
     * migrator restores programmatically ({@code setIcon(false)}).
     */
    /**
     * Iconify / deiconify. Fires {@code "ancestor"} first — which reads like a
     * mistake and is not: the JDK's {@code setIcon} opens with
     * {@code firePropertyChange("ancestor", null, getParent())}, its own comment
     * explaining that a frame iconified before it has a parent needs one
     * created so the icon can be placed on the desktop. Reproduced as written
     * (D_owed_events).
     */
    public void setIcon(boolean b) throws java.beans.PropertyVetoException {
        if (isIcon == b) return;
        firePropertyChange("ancestor", null, getParent());
        fireVetoableChange(IS_ICON_PROPERTY, isIcon, b);
        boolean old = isIcon;
        isIcon = b;
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SWindow sw && frameVisible) {
            // Hide the overlay while iconified; restore on deiconify. Live-UI hop for
            // setVisible's reason: a restore re-attaches the overlay.
            withPeerOnLiveUI(false, p -> sw.setVisible(!b));
        }
        firePropertyChange(IS_ICON_PROPERTY, old, b);
        fireInternalFrameEvent(b
                ? InternalFrameEvent.INTERNAL_FRAME_ICONIFIED
                : InternalFrameEvent.INTERNAL_FRAME_DEICONIFIED);
    }

    public boolean isMaximum() {
        return isMaximum;
    }

    /**
     * Constrained property — maximize to viewport-fill (D_internalframe_maximize). Honors the
     * vetoable change, then drives the peer geometry.
     */
    public void setMaximum(boolean b) throws java.beans.PropertyVetoException {
        if (isMaximum == b) return;
        fireVetoableChange(IS_MAXIMUM_PROPERTY, isMaximum, b);
        boolean old = isMaximum;
        isMaximum = b;
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SJInternalFrame sif) {
            withPeer(p -> sif.setMaximum(b));
        }
        firePropertyChange(IS_MAXIMUM_PROPERTY, old, b);
    }

    public boolean isSelected() {
        return isSelected;
    }

    /**
     * Constrained property — selection. Honors the vetoable change, then
     * drives the peer (which fires INTERNAL_FRAME_ACTIVATED / DEACTIVATED
     * via the relay).
     *
     * <p>The JDK's showing gate is reproduced, <b>including its asymmetry</b>:
     * selecting a frame that is not showing is refused outright, while
     * <em>de</em>selecting one goes through — so a frame selected on a desktop
     * whose window is then hidden can still be deselected, and fires the whole
     * chain doing it (measured on JDK 25). An iconified frame is gated on its
     * {@link #getDesktopIcon() desktop icon} showing instead, which is the
     * JDK's own arithmetic and not an approximation of it.
     *
     * <p>The JDK's two {@code restoreSubcomponentFocus()} calls are not
     * reproduced, and the hook is deliberately absent rather than
     * exposed-and-dead (R_no_vaadin_in_api limb 2 prefers absent):
     * every target its body could focus — the content pane, the frame
     * itself — peers on a {@code Div} or a {@code Dialog}, neither of
     * which is Vaadin {@code Focusable}, so the effect is undeliverable
     * one level down. That also costs the JDK's redundant-select branch,
     * whose whole purpose is that focus restore.
     *
     * @throws java.beans.PropertyVetoException if a
     *         {@code VetoableChangeListener} refuses the change
     */
    public void setSelected(boolean b) throws java.beans.PropertyVetoException {
        if ((isSelected == b) || (b && (isIcon ? !desktopIcon.isShowing() : !isShowing()))) {
            return;
        }
        fireVetoableChange(IS_SELECTED_PROPERTY, isSelected, b);
        boolean old = isSelected;
        isSelected = b;
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SJInternalFrame sif) {
            withPeer(p -> sif.setSelected(b));
        }
        firePropertyChange(IS_SELECTED_PROPERTY, old, b);
    }

    // ---- dispose / close-op ---------------------------------------------

    /** Close and release the frame; fires INTERNAL_FRAME_CLOSED via the relay. */
    public void dispose() {
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SWindow sw) {
            withPeer(p -> sw.dispose());
        }
        if (!isClosed) {
            isClosed = true;
            firePropertyChange(IS_CLOSED_PROPERTY, false, true);
        }
    }

    /**
     * DISPOSE honors the vetoable {@code "closed"} change (matches JDK); HIDE
     * hides and deselects; DO_NOTHING leaves the frame open.
     *
     * <p>{@code INTERNAL_FRAME_CLOSING} fires here, before the switch, and for
     * every close operation including {@code DO_NOTHING_ON_CLOSE} — which is
     * where the JDK puts it, and why the peer relay no longer fires it. It used
     * to live in the relay, so a migrator calling {@code doDefaultCloseAction()}
     * directly from a "Close" menu item got the close without the event (D_owed_events).
     */
    public void doDefaultCloseAction() {
        fireInternalFrameEvent(InternalFrameEvent.INTERNAL_FRAME_CLOSING);
        switch (defaultCloseOperation) {
            case DISPOSE_ON_CLOSE -> {
                try {
                    setClosed(true);
                } catch (java.beans.PropertyVetoException vetoed) {
                    // A listener refused the close — leave the frame open,
                    // exactly as JDK's doDefaultCloseAction swallows the veto.
                }
            }
            case HIDE_ON_CLOSE -> {
                setVisible(false);
                // The JDK deselects a hidden frame here, swallowing a veto.
                if (isSelected()) {
                    try {
                        setSelected(false);
                    } catch (java.beans.PropertyVetoException vetoed) {
                        // Same swallow as the JDK's.
                    }
                }
            }
            case DO_NOTHING_ON_CLOSE -> { /* frame stays open */ }
            default -> { /* unknown value stored verbatim per JDK; treat as no-op */ }
        }
    }

    public int getDefaultCloseOperation() {
        return defaultCloseOperation;
    }

    /**
     * JDK {@code JInternalFrame.setDefaultCloseOperation} stores the value
     * without validation (unlike JFrame/JDialog) — we match that. EXIT is
     * not meaningful here but isn't rejected, mirroring the JDK.
     */
    public void setDefaultCloseOperation(int operation) {
        this.defaultCloseOperation = operation;
    }

    // ---- Title ----------------------------------------------------------

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        String old = this.title;
        this.title = title;
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SJInternalFrame sif) {
            withPeer(p -> sif.setTitle(title));
        }
        pushedTitle = title;
        firePropertyChange(TITLE_PROPERTY, old, title);
    }

    // ---- Title-bar affordance flags -------------------------------------

    public boolean isResizable() { return resizable; }

    public void setResizable(boolean b) {
        boolean old = this.resizable;
        this.resizable = b;
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SJInternalFrame sif) {
            withPeer(p -> sif.setResizable(b));
        }
        firePropertyChange("resizable", old, b);
    }

    public boolean isClosable() { return closable; }

    public void setClosable(boolean b) {
        boolean old = this.closable;
        this.closable = b;
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SJInternalFrame sif) withPeer(p -> sif.setClosable(b));
        firePropertyChange("closable", old, b);
    }

    public boolean isMaximizable() { return maximizable; }

    public void setMaximizable(boolean b) {
        boolean old = this.maximizable;
        this.maximizable = b;
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SJInternalFrame sif) withPeer(p -> sif.setMaximizable(b));
        firePropertyChange("maximizable", old, b);
    }

    public boolean isIconifiable() { return iconable; }

    public void setIconifiable(boolean b) {
        boolean old = this.iconable;
        this.iconable = b;
        if (getPeer() instanceof com.vaadin.swingbridge.surrogates.SJInternalFrame sif) withPeer(p -> sif.setIconifiable(b));
        // Property name is "iconable", not "iconifiable" — JInternalFrame's
        // setter and its field disagree in the JDK, and the event carries the
        // field's name. Boxed Booleans because the JDK boxes explicitly here
        // rather than using the (boolean, boolean) overload (D_property_fanout_audit).
        firePropertyChange("iconable", Boolean.valueOf(old), Boolean.valueOf(b));
    }

    // ---- Frame icon + desktop icon --------------------------------------

    public vaadinx.swing.Icon getFrameIcon() {
        return frameIcon;
    }

    public void setFrameIcon(vaadinx.swing.Icon icon) {
        // No title-bar icon slot on the Vaadin Dialog header (R_vaadin_first drop); store
        // for round-trip + PCE so migrated code that reads it back is happy.
        vaadinx.swing.Icon old = this.frameIcon;
        this.frameIcon = icon;
        firePropertyChange(FRAME_ICON_PROPERTY, old, icon);
    }

    public JDesktopIcon getDesktopIcon() {
        return desktopIcon;
    }

    public void setDesktopIcon(JDesktopIcon d) {
        JDesktopIcon old = this.desktopIcon;
        this.desktopIcon = d;
        if (d != null) d.setInternalFrame(this);
        firePropertyChange("desktopIcon", old, d);
    }

    // ---- InternalFrameListener family -----------------------------------

    public void addInternalFrameListener(InternalFrameListener l) {
        listenerList.add(InternalFrameListener.class, l);
    }

    public void removeInternalFrameListener(InternalFrameListener l) {
        listenerList.remove(InternalFrameListener.class, l);
    }

    public InternalFrameListener[] getInternalFrameListeners() {
        return listenerList.getListeners(InternalFrameListener.class);
    }

    /** Dispatch one {@link InternalFrameEvent} id to registered listeners (source = this). */
    protected void fireInternalFrameEvent(int id) {
        InternalFrameListener[] listeners = listenerList.getListeners(InternalFrameListener.class);
        if (listeners.length == 0) return;
        InternalFrameEvent e = new InternalFrameEvent(this, id);
        for (InternalFrameListener l : listeners) {
            switch (id) {
                case InternalFrameEvent.INTERNAL_FRAME_OPENED      -> l.internalFrameOpened(e);
                case InternalFrameEvent.INTERNAL_FRAME_CLOSING     -> l.internalFrameClosing(e);
                case InternalFrameEvent.INTERNAL_FRAME_CLOSED      -> l.internalFrameClosed(e);
                case InternalFrameEvent.INTERNAL_FRAME_ICONIFIED   -> l.internalFrameIconified(e);
                case InternalFrameEvent.INTERNAL_FRAME_DEICONIFIED -> l.internalFrameDeiconified(e);
                case InternalFrameEvent.INTERNAL_FRAME_ACTIVATED   -> l.internalFrameActivated(e);
                case InternalFrameEvent.INTERNAL_FRAME_DEACTIVATED -> l.internalFrameDeactivated(e);
                default -> { /* out-of-range id, drop */ }
            }
        }
    }

    // ---- Misc -----------------------------------------------------------

    /** Select + best-effort front-raise (overlay order is Vaadin's, R_layouts_close_enough). */
    public void moveToFront() {
        vaadinx.EHelper.onNoop("JInternalFrame", "moveToFront");
    }

    public void moveToBack() {
        vaadinx.EHelper.onNoop("JInternalFrame", "moveToBack");
    }

    public void toFront() {
        vaadinx.EHelper.onNoop("JInternalFrame", "toFront");
    }

    public void toBack() {
        vaadinx.EHelper.onNoop("JInternalFrame", "toBack");
    }

    public String getUIClassID() {
        return "InternalFrameUI";
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JInternalFrame", "getAccessibleContext");
        return null;
    }

    // ---- Nested JDesktopIcon (D_internalframe_minimize — minimize state completeness) --------

    /**
     * Port of {@link javax.swing.JInternalFrame.JDesktopIcon}. In Swing this
     * is the small title-bar shown when a frame is iconified; in the browser
     * there is no desktop-icon strip to render it (D_internalframe_minimize), so this is a
     * minimal holder that exists for API completeness — {@code getDesktopIcon}
     * returns a real instance and migrated code can read its frame back.
     */
    public static class JDesktopIcon extends vaadinx.swing.JComponent
            implements javax.accessibility.Accessible {

        private JInternalFrame internalFrame;

        public JDesktopIcon(JInternalFrame f) {
            super(new com.vaadin.flow.component.html.Div());
            this.internalFrame = f;
            // The desktop icon is invisible until the frame is iconified; we
            // never render it (no desktop strip), so it stays non-displaying.
            setVisible(false);
        }

        public JInternalFrame getInternalFrame() {
            return internalFrame;
        }

        public void setInternalFrame(JInternalFrame f) {
            this.internalFrame = f;
        }

        public String getUIClassID() {
            return "DesktopIconUI";
        }

        public javax.accessibility.AccessibleContext getAccessibleContext() {
            vaadinx.EHelper.onUnimplemented("JInternalFrame.JDesktopIcon", "getAccessibleContext");
            return null;
        }
    }
}
