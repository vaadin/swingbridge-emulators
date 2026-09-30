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

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.swingbridge.surrogates.swing.text.JTextComponentMixin;

import javax.swing.Action;
import javax.swing.SwingConstants;
import javax.swing.event.EventListenerList;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import com.vaadin.swingbridge.surrogates.util.CssConvert;

/**
 * Surrogate for {@link javax.swing.JPasswordField}, a consumer of SD_sjtextfield's
 * {@link JTextComponentMixin}. Extends Vaadin
 * {@link PasswordField} directly (is-a). Reaches the migrator either
 * through {@code :emulators.JPasswordField} as peer, or as a richer-than-
 * stock Vaadin component in its own right.
 *
 * <h2>Why PasswordField as peer (not TextField)</h2>
 *
 * Vaadin {@link PasswordField} renders {@code <input type="password">},
 * giving a native browser-side mask glyph plus optional reveal button —
 * the exact functionality JPasswordField exists to provide. {@link PasswordField}
 * extends {@link com.vaadin.flow.component.textfield.TextFieldBase} (not
 * {@link com.vaadin.flow.component.textfield.TextField}), so SJPasswordField
 * cannot inherit from {@link SJTextField} — instead the JTextField-shaped
 * surface (columns / ActionListener-on-Enter / setAction / horizontalAlignment)
 * is duplicated here. JDK {@link javax.swing.JPasswordField} extends
 * {@link javax.swing.JTextField} so this surface is part of JPasswordField's
 * contract; the duplication is the price of Vaadin's parallel-not-shared
 * peer hierarchy. Could be lifted into a {@code JTextFieldMixin} later if
 * a third consumer arrives — for now two consumers don't justify the lift.
 *
 * <h2>Override surface against {@link JTextComponentMixin}</h2>
 *
 * <ul>
 *   <li><b>{@link #validate}</b> — visibility-widening override required
 *       by {@link com.vaadin.swingbridge.surrogates.awt.ComponentMixin}'s public
 *       {@code validate()} contract. Vaadin {@link PasswordField} declares
 *       inherited {@code validate()} as {@code protected} (input
 *       validation, not Swing's layout cycle). Same shape SJTextField
 *       and SJToggleButton take per SD_sjtextfield / SD_toggle_checkbox_first_cut.</li>
 * </ul>
 *
 * <p><b>{@code getText()} is not overridden and returns cleartext</b>, matching
 * the JDK. See SD_sjpasswordfield's correction note: {@code echoChar} is a
 * rendering concern only.</p>
 *
 * <h2>JPasswordField-specific surface</h2>
 *
 * <ul>
 *   <li><b>{@code echoChar}</b> — default {@code '*'}. State round-trip only:
 *       no getter here reads it, exactly as in the JDK, where only
 *       {@code BasicPasswordFieldUI}'s view consults it. {@link #setEchoChar}
 *       fires nothing — the JDK's setter is not bound. The rendered mask is
 *       governed by {@code <input type="password">} client-side (Vaadin exposes
 *       no server-side toggle), so the visual stays masked regardless.
 *       Toggling the masked-vs-cleartext boundary (i.e. {@code echoChar=0})
 *       WARNs per R_match_swing_errors sub-bucket (c) — see {@link #setEchoChar}
 *       for the full rationale and the {@link #setRevealButtonVisible}
 *       alternative.</li>
 *   <li><b>{@link #getPassword()}</b> — returns a fresh {@code char[]}
 *       snapshot of the Document's contents. Caller is expected to
 *       zero the array after use so the cleartext password doesn't
 *       linger in the heap as a String.</li>
 *   <li><b>{@code copy}</b> / <b>{@code cut}</b> — inherited from the
 *       mixin's {@link SHelper#onUnimplemented} default. JDK beeps and
 *       refuses to keep cleartext out of the system clipboard; our WARN
 *       and-drop is the closest equivalent and keeps clipboard-safe
 *       semantics under R_match_swing_errors.</li>
 *   <li><b>{@code getUIClassID() == "PasswordFieldUI"}</b>.</li>
 * </ul>
 *
 * <h2>JTextField-shape surface (duplicated from {@link SJTextField})</h2>
 *
 * <ul>
 *   <li><b>{@code columns}</b> — {@code setColumns(int)} writes
 *       {@code peer.setWidth} of a {@link CssConvert#columnsToCssWidth}
 *       character-column hint per R_layouts_close_enough close-enough; negative
 *       throws IAE per R_match_swing_errors.</li>
 *   <li><b>{@code horizontalAlignment}</b> — drop-and-WARN per SD_sjtextfield
 *       (Vaadin shadow DOM doesn't honor host text-align). R_match_swing_errors IAE on
 *       bad axis preserved.</li>
 *   <li><b>ActionListener wiring on Enter</b> — Vaadin
 *       {@code addKeyPressListener(Key.ENTER, ...)} fires
 *       {@link ActionEvent} with source = this and actionCommand
 *       falling back to text. Note: the {@link ActionEvent} carries the
 *       cleartext (the Document's text), not the masked form — this
 *       matches JDK JTextField behaviour and keeps form-submit handlers
 *       working without a special-case for password fields.</li>
 *   <li><b>{@code setAction(Action)}</b> — narrow propagation per JDK
 *       JTextField: {@code enabled} / {@code SHORT_DESCRIPTION} /
 *       {@code ACTION_COMMAND_KEY} only.</li>
 * </ul>
 */
public class SJPasswordField extends PasswordField implements JTextComponentMixin, EnterClaims.Claimant {

    /**
     * The session this component's writes hop through off the UI thread, read by
     * {@link com.vaadin.swingbridge.surrogates.SHelper#sessionOf}: captured here when one is
     * current, handed down by an emulator built off the UI thread, or taken at first attach.
     * Once set it never changes, since a component never leaves its session.
     */
    private volatile com.vaadin.flow.server.VaadinSession hopSession =
            com.vaadin.flow.server.VaadinSession.getCurrent();

    {
        addAttachListener(e -> hopSession = e.getSession());
    }

    /** Swing default: {@code echoChar = '*'}. */
    private char echoChar = '*';

    /** Swing default: {@code columns=0} means "no preferred width". */
    private int columns;

    /** Swing default: {@code horizontalAlignment = LEADING}. */
    private int horizontalAlignment = SwingConstants.LEADING;

    /** Public setter, no public getter (Swing quirk). Seeds outgoing ActionEvent payload. */
    private String actionCommand;

    /**
     * Type-keyed listener list for ActionListener fan-out. Lives on the
     * concrete (mirrors SJTextField). Same shape — separate from any
     * future {@code JTextArea}-shaped consumer that doesn't have
     * ActionListener support.
     */
    private final EventListenerList listenerList = new EventListenerList();

    /** Currently installed {@link Action}; null when none. */
    private Action action;

    /** Listener on {@link #action} that propagates per-property mutations. */
    private PropertyChangeListener actionPropertyChangeListener;

    public SJPasswordField() {
        this(null, null, 0);
    }

    public SJPasswordField(String text) {
        this(null, text, 0);
    }

    public SJPasswordField(int columns) {
        this(null, null, columns);
    }

    public SJPasswordField(String text, int columns) {
        this(null, text, columns);
    }

    public SJPasswordField(Document doc, String text, int columns) {
        // Root public ctor — all others funnel here. Order matters:
        // installTextComponentBindings (mixin's R_swing_is_truth sync wiring) MUST
        // run before any setText / setDocument so the listener is live
        // when the initial text reaches the Document. Enter wiring
        // installs after.
        super();
        _installSwingClass();
        if (columns < 0) {
            throw new IllegalArgumentException("columns less than zero.");
        }
        installTextComponentBindings();
        installEnterWiring();
        if (doc != null) {
            setDocument(doc);
        }
        this.columns = columns;
        applyColumnsToPeer();
        if (text != null) {
            setText(text);
        }
    }

    /**
     * Wire Enter → ActionEvent fan-out. Same shape SJTextField uses;
     * duplicated here because PasswordField doesn't share an ancestor
     * with TextField at the Vaadin level.
     */
    private void installEnterWiring() {
        addKeyPressListener(Key.ENTER,
                e -> SHelper.callSwing(this::fireActionPerformed));
    }

    /**
     * Visibility-widening override of {@link PasswordField#validate} to
     * satisfy {@link com.vaadin.swingbridge.surrogates.awt.ComponentMixin}'s public
     * {@code validate()} contract. Delegates to super so Vaadin's
     * input-validation behaviour is preserved.
     */
    @Override
    public void validate() {
        super.validate();
    }

    // --- echoChar / masking ------------------------------------------

    public char getEchoChar() {
        return echoChar;
    }

    /**
     * Set the echo character the L&amp;F would paint. Fires nothing: the JDK's
     * setter assigns, repaints and revalidates (SD_property_fanout_audit).
     *
     * <p>Vaadin {@link PasswordField} has no server-side API to flip the
     * underlying {@code <input>} element's {@code type} attribute — the
     * masked-vs-cleartext visual is governed entirely client-side by the
     * reveal button (eye icon). Since {@code echoChar} is a rendering input
     * and nothing server-side renders text here, the write is pure state:
     * it reaches no observable behaviour at all. Two divergence shapes:
     *
     * <ul>
     *   <li><b>Masked-vs-not</b> — toggling {@code echoChar=0} (JDK
     *       "show cleartext") on or off does not unmask the input
     *       visually. WARNs via {@link SHelper#onUnimplemented} per R_match_swing_errors
     *       sub-bucket (c) so migrators discover the no-op in the log.
     *       Migrated apps that toggle {@code setEchoChar(0)} for a
     *       "show password" UX should switch to
     *       {@link #setRevealButtonVisible} (default visible) and let
     *       the user click the eye icon.</li>
     *   <li><b>Glyph</b> — changing between two non-zero echo chars
     *       (default {@code '*'} → {@code '#'}) is silently accepted.
     *       The browser renders {@code <input type=password>}'s native
     *       glyph regardless; documented divergence, not a WARN, since
     *       most apps stay on the default and the user-visible impact
     *       of "wrong masking glyph but still masked" is small.</li>
     * </ul>
     */
    public void setEchoChar(char c) {
        char old = this.echoChar;
        if ((c == 0) != (old == 0)) {
            SHelper.onUnimplemented(this, "setEchoChar(visual mask state)", c);
        }
        this.echoChar = c;
        // No "echoChar" property change: the JDK's setEchoChar assigns, then
        // repaint() + revalidate(), and fires nothing (SD_property_fanout_audit).
    }

    /** True iff {@code echoChar != 0}. JDK special case: 0 means "paint cleartext". */
    public boolean echoCharIsSet() {
        return echoChar != 0;
    }

    /**
     * Return a fresh {@code char[]} snapshot of the Document's contents.
     * Caller is expected to zero the array after use so the cleartext
     * password doesn't linger in the heap. Empty Document returns an
     * empty array (matches JDK).
     */
    public char[] getPassword() {
        Document doc = getDocument();
        try {
            int len = doc.getLength();
            String s = doc.getText(0, len);
            char[] out = new char[s.length()];
            s.getChars(0, s.length(), out, 0);
            return out;
        } catch (BadLocationException e) {
            // Unreachable on a live Document; swallow per D_never_fail_on_gaps / SD_sjtextfield.
            SHelper.onUnimplemented(this, "getPassword", e);
            return new char[0];
        }
    }

    // No getText() / getText(int, int) override: JDK JPasswordField's own
    // bodies are `return super.getText(...)` — they exist solely to carry
    // @Deprecated, and echoChar has never been read by any JDK getter (only
    // by BasicPasswordFieldUI's view). The mixin's Document-verbatim defaults
    // are therefore already correct. The emulator keeps the pair to reproduce
    // the deprecation marker; a Vaadin-shaped surrogate has no such ceremony
    // to reproduce, so here they are simply absent.

    // --- Columns ------------------------------------------------------

    public int getColumns() {
        return columns;
    }

    public void setColumns(int columns) {
        if (columns < 0) {
            throw new IllegalArgumentException("columns less than zero.");
        }
        int old = this.columns;
        if (old == columns) return;
        this.columns = columns;
        applyColumnsToPeer();
        // No "columns" property change — see SJTextField.setColumns (SD_property_fanout_audit).
    }

    /**
     * Set peer width from the columns hint; see {@link CssConvert#columnsToCssWidth}.
     * Behind the {@code D_layout_owns_child_sizing} variable for the reason
     * {@link SJTextField}'s copy states — {@code columns} is a preferred width, so a
     * layout that sizes the axis itself wins over it.
     */
    private void applyColumnsToPeer() {
        setWidth(com.vaadin.swingbridge.surrogates.util.LayoutCss.prefWidth(CssConvert.columnsToCssWidth(columns)));
    }

    // --- Horizontal alignment (drop-and-WARN per SD_sjtextfield) ---------------

    public int getHorizontalAlignment() {
        return horizontalAlignment;
    }

    public void setHorizontalAlignment(int alignment) {
        if (alignment != SwingConstants.LEFT && alignment != SwingConstants.CENTER
                && alignment != SwingConstants.RIGHT && alignment != SwingConstants.LEADING
                && alignment != SwingConstants.TRAILING) {
            throw new IllegalArgumentException("horizontalAlignment");
        }
        int old = this.horizontalAlignment;
        if (old == alignment) return;
        this.horizontalAlignment = alignment;
        // Vaadin shadow DOM doesn't honor host text-align — drop and WARN
        // on non-default per R_vaadin_first. Default LEADING is silent.
        if (alignment != SwingConstants.LEADING) {
            SHelper.onUnimplemented(this, "setHorizontalAlignment", alignment);
        }
        firePropertyChange("horizontalAlignment", old, alignment);
    }

    // --- ActionListener fan-out --------------------------------------

    public synchronized void addActionListener(ActionListener l) {
        listenerList.add(ActionListener.class, l);
    }

    public synchronized void removeActionListener(ActionListener l) {
        listenerList.remove(ActionListener.class, l);
    }

    public synchronized ActionListener[] getActionListeners() {
        return listenerList.getListeners(ActionListener.class);
    }

    /** The {@link SJTextField#claimsEnter} rule: a {@code JPasswordField} is a {@code JTextField}. */
    @Override
    public boolean claimsEnter() {
        return getActionListeners().length > 0;
    }

    /**
     * Fire ActionEvent with source = this. {@code actionCommand} falls back
     * to {@link #getText()} — the same one-liner {@link SJTextField} carries,
     * because JDK JPasswordField inherits JTextField's fallback unchanged.
     */
    protected void fireActionPerformed() {
        String cmd = (actionCommand != null) ? actionCommand : getText();
        ActionEvent e = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, cmd);
        for (ActionListener l : listenerList.getListeners(ActionListener.class)) {
            l.actionPerformed(e);
        }
    }

    public void setActionCommand(String command) {
        this.actionCommand = command;
    }

    /** Public synthesizer matching JDK — useful when not Enter-triggered. */
    public void postActionEvent() {
        fireActionPerformed();
    }

    // --- Action wiring (narrow per JDK JTextField) ------------------

    public Action getAction() {
        return action;
    }

    public void setAction(Action a) {
        Action oldValue = this.action;
        if (oldValue == null ? a == null : oldValue.equals(a)) return;
        this.action = a;
        if (oldValue != null) {
            removeActionListener(oldValue);
            if (actionPropertyChangeListener != null) {
                oldValue.removePropertyChangeListener(actionPropertyChangeListener);
                actionPropertyChangeListener = null;
            }
        }
        configurePropertiesFromAction(a);
        if (a != null) {
            if (!isActionListener(a)) {
                addActionListener(a);
            }
            actionPropertyChangeListener = createActionPropertyChangeListener(a);
            a.addPropertyChangeListener(actionPropertyChangeListener);
        }
        firePropertyChange("action", oldValue, a);
    }

    private boolean isActionListener(ActionListener a) {
        for (ActionListener l : listenerList.getListeners(ActionListener.class)) {
            if (l == a) return true;
        }
        return false;
    }

    /**
     * JDK JTextField's narrow Action property set: {@code enabled} /
     * {@code SHORT_DESCRIPTION} / {@code ACTION_COMMAND_KEY}. No NAME
     * propagation (would overwrite form data) or icons or mnemonics.
     */
    protected void configurePropertiesFromAction(Action a) {
        setEnabled(a == null || a.isEnabled());
        setToolTipText(a == null ? null : (String) a.getValue(Action.SHORT_DESCRIPTION));
        setActionCommand(a == null ? null : (String) a.getValue(Action.ACTION_COMMAND_KEY));
    }

    protected PropertyChangeListener createActionPropertyChangeListener(Action a) {
        return new PasswordFieldActionPropertyChangeListener(this, a);
    }

    private static final class PasswordFieldActionPropertyChangeListener
            implements PropertyChangeListener {
        private final SJPasswordField field;
        private final Action action;

        PasswordFieldActionPropertyChangeListener(SJPasswordField field, Action action) {
            this.field = field;
            this.action = action;
        }

        @Override
        public void propertyChange(PropertyChangeEvent e) {
            field.actionPropertyChanged(action, e.getPropertyName());
        }
    }

    protected void actionPropertyChanged(Action a, String propertyName) {
        if ("enabled".equals(propertyName)) {
            setEnabled(a.isEnabled());
        } else if (Action.SHORT_DESCRIPTION.equals(propertyName)) {
            setToolTipText((String) a.getValue(Action.SHORT_DESCRIPTION));
        } else if (Action.ACTION_COMMAND_KEY.equals(propertyName)) {
            setActionCommand((String) a.getValue(Action.ACTION_COMMAND_KEY));
        }
        // NAME / MNEMONIC_KEY / icons intentionally dropped per JDK
        // JTextField's narrower-than-AbstractButton contract.
    }

    // --- L&F class id ------------------------------------------------

    @Override
    public String getUIClassID() {
        return "PasswordFieldUI";
    }

    /**
     * JDK JPasswordField.paramString chains JTextField's tail and appends the
     * raw echoChar, unmasked. We follow the same shape; underlying Vaadin
     * {@code Component.toString} is the honest host-side equivalent.
     */
    protected String paramString() {
        return "columns=" + columns
                + ",command=" + (actionCommand == null ? "" : actionCommand)
                + ",horizontalAlignment=" + horizontalAlignment
                + ",echoChar=" + echoChar;
    }

    // --- Accessibility (deferred per surrogate-wide stance) ----------

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        SHelper.onUnimplemented(this, "getAccessibleContext");
        return null;
    }
}
