/*
 * Copyright (c) 2000, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * This file is derived from OpenJDK's javax.swing.JSpinner
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

import com.vaadin.swingbridge.surrogates.SJSpinner;

/**
 * Emulator for {@link javax.swing.JSpinner}, rendered by {@link SJSpinner}, which carries
 * the per-model inner-field dispatch (Integer / Long / Double / Date / List + unknown-model
 * fallback) and the browser → model sync (SD_sjspinner).
 *
 * <p>The value lives in the {@link javax.swing.SpinnerModel}, which the emulator and the
 * surrogate share. Every getter reads the model, as the JDK's does, and never the peer, so
 * it works on any thread without a hop; the surrogate renders the model from its own model
 * listener. The emulator's {@code ChangeListener} subscribes to the model directly, as the
 * JDK's {@code ModelListener} does, so a worker setting the value hears its
 * {@code ChangeEvent} on the worker, with this spinner as the source.
 * R_leaf_peer_lockdown-locked-down (JDK leaf — private peer ctor).
 *
 * <p>Editor surface (D_jspinner_editor): {@code JSpinner.DefaultEditor / DateEditor /
 * NumberEditor / ListEditor} port as nested classes so
 * {@code setEditor(new JSpinner.DateEditor(spinner, "yyyy-MM-dd"))} compiles and
 * renders. Only DateEditor's pattern is plumbed through to the Vaadin DatePicker
 * (via {@code SJSpinner.setDatePattern}); the other families drop-and-WARN at
 * setEditor (R_match_swing_errors sub-bucket (c) — Vaadin's IntegerField / NumberField don't
 * accept DecimalFormat patterns, and ListEditor is already covered by
 * SJSpinner's Select dispatch). The editor's synthetic off-DOM
 * JFormattedTextField exists so {@code editor.getTextField()} doesn't NPE.
 */
public class JSpinner extends vaadinx.swing.JComponent implements javax.accessibility.Accessible {

    /** Private and named as in the JDK; the surrogate renders the same instance. */
    private javax.swing.SpinnerModel model;

    /**
     * Re-fires the model's {@code ChangeEvent} as this spinner's. Subscribed eagerly,
     * where the JDK subscribes on the first {@link #addChangeListener}; the difference is
     * only visible in the model's own listener list.
     */
    private final javax.swing.event.ChangeListener modelListener =
            e -> vaadinx.EHelper.relayModelEvent(this::fireStateChanged);

    public JSpinner() {
        this(new SJSpinner());
    }

    public JSpinner(javax.swing.SpinnerModel model) {
        this(new SJSpinner(model));
    }

    private JSpinner(SJSpinner peer) {
        // Peer lock-down per R_leaf_peer_lockdown: javax.swing.JSpinner is a leaf in the
        // public Swing hierarchy. The seam through which the public
        // ctors pass their chosen SJSpinner variant up is private +
        // typed-narrow, so user-code subclasses can't reach it to swap
        // the peer type.
        super(peer);
        // The peer ctor took the JDK ctor's model (and threw its NPE for null); read back
        // while the peer has never been attached, so the read needs no lock.
        model = peer.getModel();
        model.addChangeListener(modelListener);
    }

    /** Narrow the peer to its SJSpinner type. */
    private SJSpinner spinner() {
        return (SJSpinner) getPeer();
    }

    // ---- JSpinner API surface (through the model, as in the JDK) ----

    public java.lang.Object getValue() {
        return getModel().getValue();
    }

    public void setValue(java.lang.Object value) {
        getModel().setValue(value);
    }

    public javax.swing.SpinnerModel getModel() {
        return model;
    }

    /**
     * Unlike the JDK, installs no new editor for the new model: the peer renders the
     * model without one (see {@link #createEditor}).
     */
    public void setModel(javax.swing.SpinnerModel newModel) {
        if (newModel == null) {
            throw new IllegalArgumentException("null model");
        }
        if (newModel.equals(model)) return;
        javax.swing.SpinnerModel old = model;
        model = newModel;
        old.removeChangeListener(modelListener);
        newModel.addChangeListener(modelListener);
        withPeer(p -> spinner().setModel(newModel));
        firePropertyChange("model", old, newModel);
    }

    public java.lang.Object getNextValue() {
        return getModel().getNextValue();
    }

    public java.lang.Object getPreviousValue() {
        return getModel().getPreviousValue();
    }

    // ---- ChangeListener fan-out (emulator-local, source=this) ----

    public void addChangeListener(javax.swing.event.ChangeListener l) {
        listenerList.add(javax.swing.event.ChangeListener.class, l);
    }

    public void removeChangeListener(javax.swing.event.ChangeListener l) {
        listenerList.remove(javax.swing.event.ChangeListener.class, l);
    }

    public javax.swing.event.ChangeListener[] getChangeListeners() {
        return listenerList.getListeners(javax.swing.event.ChangeListener.class);
    }

    protected void fireStateChanged() {
        // Source=this so user casts `(JSpinner) e.getSource()` work.
        // Shared event instance per fire — ChangeEvent carries no payload.
        javax.swing.event.ChangeEvent event = new javax.swing.event.ChangeEvent(this);
        for (javax.swing.event.ChangeListener l :
                listenerList.getListeners(javax.swing.event.ChangeListener.class)) {
            l.stateChanged(event);
        }
    }

    // ---- Editor surface (D_jspinner_editor) ----
    //
    // JDK JSpinner builds a JSpinner.NumberEditor / DateEditor / ListEditor
    // around a JFormattedTextField that JSpinner renders inline. Our peer
    // is the spinner's underlying Vaadin field (IntegerField / DatePicker /
    // Select / …) chosen by SJSpinner's per-model dispatch; the JDK
    // editor chrome doesn't render, but it does carry information user
    // code reads back (DateEditor's pattern, getTextField() for
    // setColumns / setText / etc.). Editor port goals:
    //
    //   - DateEditor's date format pattern propagates through to
    //     DatePicker.setI18n via SJSpinner.setDatePattern (the R_layouts_close_enough
    //     best-effort plumbing closing the user-stated date-of-birth
    //     formatting need).
    //   - getTextField() returns a real synthetic JFormattedTextField so
    //     migrated code that introspects the editor doesn't NPE.
    //   - NumberEditor / ListEditor / generic setEditor(JComponent) stay
    //     drop-and-WARN at setEditor per R_match_swing_errors sub-bucket (c) — Vaadin's
    //     IntegerField / NumberField don't accept DecimalFormat patterns,
    //     and SJSpinner's Select<Object> dispatch already covers the
    //     SpinnerListModel render shape.

    /** Currently installed editor or null. Field-shadow only; the real UI is the spinner peer. */
    private vaadinx.swing.JComponent editor;

    public vaadinx.swing.JComponent getEditor() {
        return editor;
    }

    public void setEditor(vaadinx.swing.JComponent editor) {
        if (editor == null) {
            throw new IllegalArgumentException("null editor");
        }
        vaadinx.swing.JComponent old = this.editor;
        if (old == editor) return;
        this.editor = editor;
        // DateEditor — pull the format pattern and route through to the
        // underlying DatePicker via the surrogate. Other editor types are
        // stored for getEditor round-trip but don't drive the peer.
        if (editor instanceof DateEditor de) {
            String pattern = de.getDateFormatPattern();
            withPeer(p -> spinner().setDatePattern(pattern));
        } else if (editor instanceof NumberEditor) {
            // R_match_swing_errors (c): Vaadin's IntegerField / NumberField don't accept
            // DecimalFormat patterns. Format is field-shadowed inside
            // NumberEditor so getFormat round-trips, but the spinner
            // continues to render the locale default.
            vaadinx.EHelper.onUnimplemented("JSpinner", "setEditor(NumberEditor)", editor);
        } else if (editor instanceof ListEditor) {
            // R_match_swing_errors (c): SpinnerListModel already dispatches to Vaadin
            // Select<Object> in SJSpinner.createInnerFieldFor; the JDK
            // ListEditor chrome (combo-with-typing) doesn't add anything
            // the peer doesn't already handle. Stored for round-trip.
            vaadinx.EHelper.onUnimplemented("JSpinner", "setEditor(ListEditor)", editor);
        } else {
            // Generic JComponent — user-supplied custom editor. We can't
            // render it (peer is the spinner's own Vaadin field), so the
            // best we can do is field-shadow + WARN.
            vaadinx.EHelper.onUnimplemented("JSpinner", "setEditor(JComponent)", editor);
        }
        firePropertyChange("editor", old, editor);
    }

    protected vaadinx.swing.JComponent createEditor(javax.swing.SpinnerModel model) {
        // JDK builds a default editor matching the model type; we never
        // call this internally (the peer renders without an editor) so a
        // null-and-WARN is harmless until a migrator overrides createEditor
        // to plug in a custom one.
        vaadinx.EHelper.onUnimplemented("JSpinner", "createEditor", model);
        return null;
    }

    public void commitEdit() throws java.text.ParseException {
        // Peer edits round-trip into the model on every ValueChange, so
        // the model is always consistent at call time. No-op matches the
        // surrogate's behaviour; throws ParseException stays on the
        // signature for compat.
    }

    // ---- Editor nested classes (D_jspinner_editor) ----

    /**
     * Emulator for {@link javax.swing.JSpinner.DefaultEditor}. Extends
     * {@link vaadinx.swing.JPanel} per the JDK shape; carries a synthetic
     * {@link vaadinx.swing.JFormattedTextField} accessible via
     * {@link #getTextField()} so migrated code that reaches through it
     * (e.g. {@code editor.getTextField().setColumns(8)}) doesn't NPE. The
     * synthetic field is never attached to the DOM — the spinner's
     * underlying Vaadin peer is what actually renders.
     */
    public static class DefaultEditor extends vaadinx.swing.JPanel
            implements javax.swing.event.ChangeListener, java.beans.PropertyChangeListener,
                       vaadinx.awt.LayoutManager {

        private final JSpinner spinner;
        private final vaadinx.swing.JFormattedTextField ftf;

        public DefaultEditor(JSpinner spinner) {
            this(spinner, new vaadinx.swing.JFormattedTextField());
        }

        /**
         * Subclass-only seam letting {@link DateEditor} install a
         * pre-configured field (with the appropriate formatter) — avoids
         * the cross-family setFormatter swap WARN that the
         * "construct plain, mutate later" JDK shape would trigger against
         * D_formatter_swap_rules's strategy lock-in.
         */
        DefaultEditor(JSpinner spinner, vaadinx.swing.JFormattedTextField ftf) {
            this.spinner = spinner;
            this.ftf = ftf;
        }

        public JSpinner getSpinner() {
            return spinner;
        }

        public vaadinx.swing.JFormattedTextField getTextField() {
            return ftf;
        }

        @Override
        public void stateChanged(javax.swing.event.ChangeEvent e) {
            // JDK pushes the spinner's value through the formatted field
            // here; for us the peer already mirrors the model on every
            // value change, so this is a no-op. Documented divergence —
            // user code that overrides stateChanged for custom display
            // logic continues to receive the event but the inherited
            // behaviour is gone.
        }

        @Override
        public void propertyChange(java.beans.PropertyChangeEvent e) {
            // JDK uses this to keep the spinner's model in sync with
            // the formatted field's value. We bypass — peer edits go
            // through SJSpinner's value-change listener directly into
            // the model.
        }

        public void commitEdit() throws java.text.ParseException {
            // No formatter to parse through — the peer's value is
            // always already authoritative.
        }

        public void dismiss(JSpinner spinner) {
            // JDK detaches change-listener wiring here. We never wired
            // any (the peer drives the model directly), so no-op.
        }

        // ---- LayoutManager (the editor lays out its own single child) ----
        // The JDK's bodies verbatim — plain Insets/Dimension arithmetic that needs no
        // Vaadin counterpart. Deliberately *not* installed: the JDK ctor's
        // setLayout(this) is omitted because this editor never add()s the formatted
        // field (it is never attached to the DOM), so layoutContainer would have
        // nothing to lay out and installing the manager would only route through
        // M1D_custom_layoutmanager's WARN fallback. Layout is R_layouts_close_enough-
        // exempt; the members exist so the type hierarchy matches (D_hierarchy_parity).

        public void removeLayoutComponent(vaadinx.awt.Component child) {
        }

        public void addLayoutComponent(String name, vaadinx.awt.Component child) {
        }

        public java.awt.Dimension preferredLayoutSize(vaadinx.awt.Container parent) {
            java.awt.Dimension preferredSize = insetSize(parent);
            if (parent.getComponentCount() > 0) {
                java.awt.Dimension childSize = getComponent(0).getPreferredSize();
                preferredSize.width += childSize.width;
                preferredSize.height += childSize.height;
            }
            return preferredSize;
        }

        public java.awt.Dimension minimumLayoutSize(vaadinx.awt.Container parent) {
            java.awt.Dimension minimumSize = insetSize(parent);
            if (parent.getComponentCount() > 0) {
                java.awt.Dimension childSize = getComponent(0).getMinimumSize();
                minimumSize.width += childSize.width;
                minimumSize.height += childSize.height;
            }
            return minimumSize;
        }

        private java.awt.Dimension insetSize(vaadinx.awt.Container parent) {
            java.awt.Insets insets = parent.getInsets();
            int w = insets.left + insets.right;
            int h = insets.top + insets.bottom;
            return new java.awt.Dimension(w, h);
        }

        public void layoutContainer(vaadinx.awt.Container parent) {
            if (parent.getComponentCount() > 0) {
                java.awt.Insets insets = parent.getInsets();
                int w = parent.getWidth() - (insets.left + insets.right);
                int h = parent.getHeight() - (insets.top + insets.bottom);
                getComponent(0).setBounds(insets.left, insets.top, w, h);
            }
        }
    }

    /**
     * Emulator for {@link javax.swing.JSpinner.DateEditor}. Stores the
     * date-format pattern; {@link JSpinner#setEditor} reads it back and
     * routes to {@link SJSpinner#setDatePattern} which applies a
     * {@link com.vaadin.flow.component.datepicker.DatePicker.DatePickerI18n}
     * to the underlying {@link com.vaadin.flow.component.datepicker.DatePicker}
     * (R_layouts_close_enough best-effort — full {@link java.text.SimpleDateFormat} syntax
     * isn't covered; time-component patterns drop the time silently).
     */
    public static class DateEditor extends DefaultEditor {

        private final vaadinx.text.SimpleDateFormat format;
        private final String pattern;

        public DateEditor(JSpinner spinner) {
            this(spinner, javax.swing.JSpinner.DateEditor.class.getName(), null);
            // JDK's no-pattern ctor uses the locale's default short-date
            // format; we surface the gap and pin to a sensible ISO default
            // so the field still renders. Migrated code that constructs
            // DateEditor without a pattern is rare — the typical call
            // shape is the (spinner, pattern) ctor.
            vaadinx.EHelper.onUnimplemented("JSpinner.DateEditor",
                    "ctor without pattern (locale-default short-date format)", spinner);
        }

        public DateEditor(JSpinner spinner, String dateFormatPattern) {
            this(spinner, dateFormatPattern, dateFormatPattern);
        }

        private DateEditor(JSpinner spinner, String tag, String dateFormatPattern) {
            super(spinner, buildDateTextField(dateFormatPattern));
            this.pattern = dateFormatPattern;
            // vaadinx.text.SimpleDateFormat, not the JDK's: R_no_vaadin_in_api limb 1 makes
            // the ported type the one getFormat() must hand back, and it is a
            // subclass of the JDK's, so the DateFormatter path below is
            // unaffected. Construction never touches a session, so this is
            // safe at spinner-construction time.
            this.format = dateFormatPattern == null
                    ? new vaadinx.text.SimpleDateFormat()
                    : new vaadinx.text.SimpleDateFormat(dateFormatPattern);
        }

        private static vaadinx.swing.JFormattedTextField buildDateTextField(String pattern) {
            if (pattern == null) {
                return new vaadinx.swing.JFormattedTextField();
            }
            // Construct directly with a DateFormatter so the strategy is
            // pinned to DateStrategy — avoids the cross-family setFormatter
            // swap WARN that "plain field + setFormatter(DateFormatter)"
            // would trigger against D_formatter_swap_rules.
            javax.swing.text.DateFormatter formatter =
                    new javax.swing.text.DateFormatter(new java.text.SimpleDateFormat(pattern));
            return new vaadinx.swing.JFormattedTextField(formatter);
        }

        public vaadinx.text.SimpleDateFormat getFormat() {
            return format;
        }

        public javax.swing.SpinnerDateModel getModel() {
            javax.swing.SpinnerModel m = getSpinner().getModel();
            if (m instanceof javax.swing.SpinnerDateModel sdm) return sdm;
            // JDK throws ClassCastException at the JSpinner.getEditor() ->
            // (DateEditor) cast time; here the editor exists but is wrong-
            // model. R_match_swing_errors: surface the gap, return null so the misuse fails
            // loudly on the next dereference.
            vaadinx.EHelper.onUnimplemented("JSpinner.DateEditor",
                    "getModel called with non-Date spinner model", m);
            return null;
        }

        /** Package-private accessor used by {@link JSpinner#setEditor}. */
        String getDateFormatPattern() {
            return pattern;
        }
    }

    /**
     * Emulator for {@link javax.swing.JSpinner.NumberEditor}. Drop-and-WARN
     * per R_match_swing_errors sub-bucket (c) — Vaadin's IntegerField / NumberField don't
     * accept JDK DecimalFormat patterns. The format is stored so
     * {@code editor.getFormat()} round-trips; the actual UI continues
     * to render through the locale default.
     */
    public static class NumberEditor extends DefaultEditor {

        private final java.text.DecimalFormat format;

        public NumberEditor(JSpinner spinner) {
            super(spinner);
            this.format = new java.text.DecimalFormat();
        }

        public NumberEditor(JSpinner spinner, String decimalFormatPattern) {
            super(spinner);
            this.format = decimalFormatPattern == null
                    ? new java.text.DecimalFormat()
                    : new java.text.DecimalFormat(decimalFormatPattern);
        }

        public java.text.DecimalFormat getFormat() {
            return format;
        }

        public javax.swing.SpinnerNumberModel getModel() {
            javax.swing.SpinnerModel m = getSpinner().getModel();
            if (m instanceof javax.swing.SpinnerNumberModel snm) return snm;
            vaadinx.EHelper.onUnimplemented("JSpinner.NumberEditor",
                    "getModel called with non-Number spinner model", m);
            return null;
        }
    }

    /**
     * Emulator for {@link javax.swing.JSpinner.ListEditor}. Drop-and-WARN
     * per R_match_swing_errors sub-bucket (c) — SpinnerListModel already dispatches to
     * Vaadin Select&lt;Object&gt; in SJSpinner; the editor chrome
     * (combo-with-typing) adds nothing the peer doesn't already cover.
     */
    public static class ListEditor extends DefaultEditor {

        public ListEditor(JSpinner spinner) {
            super(spinner);
        }

        public javax.swing.SpinnerListModel getModel() {
            javax.swing.SpinnerModel m = getSpinner().getModel();
            if (m instanceof javax.swing.SpinnerListModel slm) return slm;
            vaadinx.EHelper.onUnimplemented("JSpinner.ListEditor",
                    "getModel called with non-List spinner model", m);
            return null;
        }
    }

    // ---- L&F stubs (matching sibling components) ----

    public void updateUI() {
        // L&F swap — same rationale as JSlider / JButton / JCheckBox.
        // Vaadin owns the DOM; no pluggable UI.
    }

    public java.lang.String getUIClassID() {
        return "SpinnerUI";
    }

    public javax.swing.plaf.SpinnerUI getUI() {
        // Same "no pluggable UI" stance as JSlider.getUI. Return null
        // directly rather than chain to super (which returns ComponentUI
        // and wouldn't narrow to SpinnerUI).
        return null;
    }

    public void setUI(javax.swing.plaf.SpinnerUI ui) {
        // L&F delegate install — no-op, matching the null getUI above.
    }

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JSpinner", "getAccessibleContext");
        return null;
    }

}
