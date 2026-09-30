/*
 * Copyright (c) 1997, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.JButton
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

/**
 * Emulator for {@link javax.swing.JButton} — a thin delegating shell over its
 * {@link com.vaadin.swingbridge.surrogates.SJButton} peer. Responsibility splits across the two:
 *
 * <ul>
 *   <li><b>Surrogate</b> — the ButtonModel-driven click/armed/pressed pulse,
 *       the {@code AbstractButtonMixin} Action wiring, and the rendered Vaadin
 *       Button peer.</li>
 *   <li><b>Emulator (here + {@link AbstractButton})</b> — the Swing-shape API
 *       migrated code compiles against: {@code ActionListener} with
 *       {@code source == this} JButton, PCEs on the emulator's own
 *       {@code PropertyChangeSupport}, and the JButton-specific
 *       {@code defaultCapable} / {@code isDefaultButton} / {@code paramString}
 *       surface.</li>
 * </ul>
 *
 * Field shadows for visual flags / alignment / margin / multiClick /
 * state-conditional icons live on {@link AbstractButton}, not the surrogate:
 * SJButton drops them per R_vaadin_first (bucket B/C), so the emulator is the only side
 * where full JDK round-trip survives (R_swing_is_truth). Leaf peer is locked down per R_leaf_peer_lockdown.
 */
public class JButton extends vaadinx.swing.AbstractButton implements javax.accessibility.Accessible {

    // Swing default is true: a freshly-constructed JButton is eligible to
    // become the root pane's default button. We don't model JRootPane's
    // default-button wiring yet (isDefaultButton stays stubbed-as-false),
    // but honoring the flag round-trips setDefaultCapable/getDefaultCapable
    // for user code that reads it back.
    private boolean defaultCapable = true;

    public JButton(java.lang.String text, vaadinx.swing.Icon icon) {
        // Root ctor for the public surface: instantiate the SJButton
        // surrogate (SD_sjbutton — peer-side ButtonModel pulse +
        // AbstractButtonMixin Action wiring + rendered Vaadin Button),
        // then funnel through AbstractButton.init(text, icon). JDK follows
        // the same shape — every JButton ctor converges on init so subclass
        // overrides of init() fire exactly once (R_swing_is_truth). Icon rendering for
        // ImageIcon flows through the AbstractButton.setIcon path (Path 1
        // via vaadinx.EHelper.toVaadinIconComponent, lands on SJButton via
        // the inherited Vaadin Button.setIcon).
        //
        // Peer lock-down per R_leaf_peer_lockdown: javax.swing.JButton is a leaf in the
        // public Swing hierarchy (its only JDK subclasses live in
        // javax.swing.plaf.* which we drop per R_match_swing_errors sub-bucket b), so the
        // protected (Component peer) ctor is gone and super(...) takes
        // the SJButton directly. User-code subclasses inherit the locked
        // peer; behavioural overrides still work, and rendering overrides
        // are out of scope per R_match_swing_errors (b).
        super(new com.vaadin.swingbridge.surrogates.SJButton());
        init(text, icon);
    }

    public JButton(javax.swing.Action action) {
        // JDK pattern: this(); setAction(a). Chaining to the no-arg ctor
        // means init(null, null) still fires, so subclass init() overrides
        // see the same single call they would under real Swing. setAction
        // itself is still a stub (Action.accept / PCE wiring is substantial);
        // peer is real so it can land later without reshaping the ctor.
        this();
        setAction(action);
    }

    public JButton(java.lang.String text) {
        this(text, null);
    }

    public JButton(vaadinx.swing.Icon icon) {
        this(null, icon);
    }

    public JButton() {
        this(null, null);
    }

    protected java.lang.String paramString() {
        // JDK's JButton.paramString appends only ",defaultCapable=<bool>".
        // Chain up so AbstractButton's text/margin/flags and Container's
        // layout= line come first — matches Swing's toString shape.
        return super.paramString() + ",defaultCapable=" + defaultCapable;
    }

    public void removeNotify() {
        // JDK JButton.removeNotify clears this button from the root pane's
        // defaultButton slot when applicable. We don't model JRootPane
        // default-button wiring (isDefaultButton stays false), so
        // super.removeNotify (AbstractButton → Component's onNoop) covers
        // it. Override kept so the rationale survives regeneration.
        super.removeNotify();
    }

    public java.lang.String getUIClassID() {
        // Swing constant; some user/library code reads it reflectively to
        // register custom UIs against UIManager. L&F doesn't drive rendering
        // here (Vaadin owns the DOM), but returning the canonical value
        // keeps such code from NPEing on the lookup key.
        return "ButtonUI";
    }

    public void updateUI() {
        // L&F swap — no-op for us. Swing's updateUI reinstalls the ButtonUI
        // from UIManager; our "UI" is the Vaadin peer, which isn't
        // pluggable. Safe to ignore: user code calls this after changing
        // L&F, which we don't expose either.
    }

    public boolean isDefaultButton() {
        // Would be true only when this button is the root pane's
        // defaultButton. JRootPane default-button wiring isn't modeled, so
        // false is the honest answer today. Leaving the stub comment-only
        // (no onUnimplemented) because a `false` return is correct state,
        // not a missing feature — user code branching on this just takes
        // the non-default path.
        return false;
    }

    public boolean isDefaultCapable() {
        return defaultCapable;
    }

    public void setDefaultCapable(boolean defaultCapable) {
        // Swing fires "defaultCapable" PropertyChangeEvent; firePropertyChange
        // dedups equal values so a no-op set doesn't re-notify.
        boolean old = this.defaultCapable;
        this.defaultCapable = defaultCapable;
        firePropertyChange("defaultCapable", old, defaultCapable);
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JButton", "getAccessibleContext");
        return null;
    }

}
