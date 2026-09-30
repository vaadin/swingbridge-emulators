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

import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.swingbridge.surrogates.awt.event.SWindowEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Surrogate for {@link java.awt.Frame}. Extends {@link SWindow} (the
 * Window-level peer-sync, owner tree, listener families, lifecycle) and
 * adds the Frame-specific surface: {@code title} (mirrored to
 * {@link Dialog#setHeaderTitle}), the close-X icon in the Dialog header
 * (Frame chrome — JDK Window has no chrome by default; only Frame and
 * Dialog do), and the AWT-Frame state stubs (state / resizable /
 * undecorated). The JFrame-shaped layer (default close operation,
 * content-pane routing, JRootPane holder) lives on {@link SJFrame}.
 *
 * <h2>Owner type</h2>
 *
 * SFrame's owner-taking ctor accepts {@code SFrame owner} for backward
 * compatibility (callers existed before SWindow landed). The underlying
 * field is {@link SWindow#owner} typed {@code SWindow}; {@link #getOwner()}
 * inherits from SWindow and returns {@code SWindow}. Tests that assert
 * {@code frame.getOwner() == otherFrame} work because the comparison is
 * by reference, not by static type. JDK shape is Frame.getOwner inheriting
 * Window.getOwner returning Window; ours matches.
 *
 * <h2>Add / remove on a bare SFrame</h2>
 *
 * {@code SFrame.add(component)} routes through {@link Dialog}'s default
 * add, which puts the child in the overlay's content slot. There is no
 * intermediate content-pane — JDK {@link java.awt.Frame#add} also adds
 * directly without a content-pane indirection (the contentPane indirection
 * is JFrame's). {@link SJFrame} overrides add/remove to route through its
 * own contentPane, matching JFrame's post-Java-5 contract.
 */
public class SFrame extends SWindow {

    /**
     * AWT's never-null title, coerced from null to empty string per
     * {@link java.awt.Frame} contract. Mirrors {@link Dialog#setHeaderTitle}
     * on every set.
     */
    private String title = "";

    // ---- Constructors ----------------------------------------------------

    /** Untitled, no owner. */
    public SFrame() {
        this(null, null);
    }

    /** Titled, no owner. Null title coerces to empty string. */
    public SFrame(String title) {
        this(title, null);
    }

    /** Untitled, with owner. Null owner is a top-level frame. */
    public SFrame(SFrame owner) {
        this(null, owner);
    }

    /**
     * Full ctor. Null title coerces to empty; null owner makes this
     * top-level. SWindow's ctor handles owner registration + Dialog peer
     * subscription; SFrame layers title + the close-X chrome on top.
     */
    public SFrame(String title, SFrame owner) {
        super(owner);
        if (title != null && !title.isEmpty()) {
            this.title = title;
            setHeaderTitle(title);
        }
        installHeaderCloseButton();
        // AWT Frame/Dialog are resizable by default; the Vaadin Dialog peer
        // is not (its default is false). Seed the native flag so a migrated
        // window that never calls setResizable still resizes like AWT's.
        setResizable(true);
        // AWT windows are always user-movable via their title bar — there is
        // no setDraggable in Swing to map. Vaadin Dialog defaults draggable
        // to false, so seed it true to reproduce that always-movable shape.
        // The header (title + close-X chrome) is the drag handle.
        setDraggable(true);
    }

    // ---- Header close button (Dialog chrome) ----------------------------

    /**
     * Adds a small "✕" {@link com.vaadin.flow.component.icon.Icon} to the
     * Dialog's header slot. Vaadin Dialog renders a header bar when
     * {@code setHeaderTitle} is non-empty or the header has children, but
     * does not provide a close affordance by default — AWT Frame's title
     * bar always has one. The icon is clickable (Vaadin {@code Icon}
     * implements {@code ClickNotifier}) and the body funnels through
     * {@link SHelper#callSwing} (R_callswing_envelope), routing through
     * {@code processWindowEvent} with {@link SWindowEvent#WINDOW_CLOSING}
     * so {@link SJFrame}'s default-close-operation policy fires (HIDE /
     * DISPOSE / DO_NOTHING / EXIT throw all handled identically to ESC
     * and outside-click).
     *
     * <p>Deliberately not a Vaadin {@code Button} — using a Button here
     * pollutes test queries (Karibu-Testing's {@code _get<Button>()} would
     * pick up the close-X alongside the user's own buttons in every frame
     * fixture). An icon with a click listener is the chrome we need
     * without the listener-list overlap.
     */
    private void installHeaderCloseButton() {
        com.vaadin.flow.component.icon.Icon closeIcon =
                new com.vaadin.flow.component.icon.Icon(
                        com.vaadin.flow.component.icon.VaadinIcon.CLOSE);
        closeIcon.getElement().setAttribute("aria-label", "Close");
        closeIcon.getElement().setAttribute("role", "button");
        closeIcon.getElement().setAttribute("tabindex", "0");
        closeIcon.getStyle().set("cursor", "pointer");
        closeIcon.addClickListener(e -> SHelper.callSwing(() ->
                processWindowEvent(new SWindowEvent(this, SWindowEvent.WINDOW_CLOSING))));
        getHeader().add(closeIcon);
    }

    // ---- Title (Frame surface) ------------------------------------------

    /** Never-null per AWT contract. */
    public String getTitle() {
        return title;
    }

    /**
     * AWT's Frame coerces null title to empty string, and getTitle is
     * documented as never-null. Mirrors {@link Dialog#setHeaderTitle} on
     * every set; fires {@code "title"} PCE.
     */
    public void setTitle(String title) {
        String normalized = title != null ? title : "";
        String old = this.title;
        if (old.equals(normalized)) return;
        this.title = normalized;
        setHeaderTitle(normalized);
        firePropertyChange("title", old, normalized);
    }

    // ---- Static live-graph accessors (Frame-typed filter on getWindows) -

    /**
     * Every {@link SFrame} reachable from the current {@link com.vaadin.flow.component.UI}.
     * Filters {@link SWindow#getWindows()} by {@code instanceof SFrame}.
     */
    public static List<SFrame> getFrames() {
        List<SFrame> out = new ArrayList<>();
        for (SWindow w : SWindow.getWindows()) {
            if (w instanceof SFrame f) out.add(f);
        }
        return out;
    }

    /** Subset of {@link #getFrames()} with {@code owner == null}. */
    public static List<SFrame> getOwnerlessFrames() {
        List<SFrame> out = new ArrayList<>();
        for (SFrame f : getFrames()) if (f.getOwner() == null) out.add(f);
        return out;
    }

    // ---- Frame-level state stubs ----------------------------------------
    //
    // Browser tabs don't iconify or maximize in the AWT sense; always
    // NORMAL. Setter WARNs only when asked for a non-default bit so the
    // no-op redundant-set case doesn't spam logs.

    /** AWT legacy state: NORMAL (0) or ICONIFIED (1). Always NORMAL here. */
    public int getState() {
        return java.awt.Frame.NORMAL;
    }

    /** WARN on non-NORMAL; silent on NORMAL (redundant set). */
    public void setState(int state) {
        if (state != java.awt.Frame.NORMAL) {
            SHelper.onUnimplemented(this, "setState", state);
        }
    }

    /**
     * Bitmask form of {@link #getState()}. Always NORMAL — browser tabs
     * don't iconify or maximize in the AWT sense.
     */
    public int getExtendedState() {
        return java.awt.Frame.NORMAL;
    }

    /** WARN on non-NORMAL; silent on NORMAL. */
    public void setExtendedState(int state) {
        if (state != java.awt.Frame.NORMAL) {
            SHelper.onUnimplemented(this, "setExtendedState", state);
        }
    }

    /**
     * Whether the end user may resize this frame by dragging its edges.
     * Reads the native Vaadin {@link Dialog#isResizable()} — the frame
     * renders as a Dialog overlay, and Vaadin Dialog draws drag-to-resize
     * grips when resizable. Seeded to {@code true} in the ctor to match
     * AWT's resizable-by-default Frame/Dialog.
     */
    @Override
    public boolean isResizable() {
        return super.isResizable();
    }

    /**
     * Drive the native Vaadin {@link Dialog#setResizable(boolean)} and fire the
     * {@code "resizable"} property change {@code java.awt.Frame.setResizable}
     * fires. Not a no-op: the "browser resizes the viewport regardless" premise
     * holds for the OS window but not for a frame, which renders as a
     * fixed-size Dialog overlay whose own resize affordance Vaadin exposes
     * right here — honoring the flag keeps a resizable Swing window resizable
     * in the browser.
     *
     * <p>Because the effect is fully honoured, there is no R_vaadin_first drop to hide
     * behind: the property is real here, so its notification is owed (SD_property_fanout_audit).
     */
    @Override
    public void setResizable(boolean b) {
        boolean old = isResizable();
        super.setResizable(b);
        firePropertyChange("resizable", old, b);
    }

    /** Vaadin-first read-back of {@link #setUndecorated(boolean)}. */
    public boolean isUndecorated() {
        return isUndecoratedChrome();
    }

    /**
     * Strip (or restore) the window chrome via
     * {@link SWindow#setUndecoratedChrome(boolean)} — hides the header
     * (title + close-X), zeroes padding and border radius.
     * Unlike JDK {@code Frame.setUndecorated} this doesn't throw when
     * the window is displayable — the CSS class toggles live, so the
     * JDK's not-while-displayable restriction would only add a failure
     * mode we don't have; the displayable-time throw stays on the
     * emulator layer where the JDK contract is the API surface.
     */
    public void setUndecorated(boolean b) {
        setUndecoratedChrome(b);
    }

    /**
     * AWT default: false — JFrames normally use OS-native chrome unless
     * the L&amp;F opts in. We have no L&amp;F layer at all (R_layouts_close_enough) and the Dialog
     * peer always renders Vaadin's overlay chrome, so false is both the
     * honest post-construction answer and the correct one for any code
     * branching on "am I drawing my own chrome?".
     */
    public static boolean isDefaultLookAndFeelDecorated() {
        return false;
    }

    /** WARN on {@code true}; matches {@link #setUndecorated(boolean)}. */
    public static void setDefaultLookAndFeelDecorated(boolean b) {
        if (b) SHelper.onUnimplemented(SFrame.class, "setDefaultLookAndFeelDecorated", b);
    }

    // ---- paramString ----------------------------------------------------

    /**
     * Chain super ({@link SWindow#paramString()} = "visible=...") and
     * prepend the title. {@link SJFrame#paramString()} extends this with
     * the defaultCloseOperation report.
     */
    @Override
    public String paramString() {
        StringBuilder sb = new StringBuilder();
        if (!title.isEmpty()) sb.append("title=").append(title).append(',');
        sb.append(super.paramString());
        return sb.toString();
    }
}
