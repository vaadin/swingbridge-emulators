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
 * This file is derived from OpenJDK's javax.swing.JOptionPane
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing;

// Hand-finished emulator for javax.swing.JOptionPane. The
// confirm/error/message/input dialog primitive. Emulator-only per SD_no_sjoptionpane
// — no SJOptionPane surrogate. The peer is a plain Div populated with
// child JLabel / JButton / JTextField / JComboBox emulators (which
// each carry their own surrogates), so Karibu lookups into the option
// pane's content work the same as for any other JComponent subtree.
//
// Static factories (showMessageDialog / showConfirmDialog /
// showInputDialog / showOptionDialog) build a JOptionPane, host it on
// a JDialog via createDialog(parent, title), call dialog.setVisible(true)
// — which parks the calling virtual thread on the JDialog modal-park
// machinery (SD_modal_park) — and translate the user's chosen button to the
// JDK return-code shape (int / String / Object). Caller must be in an
// EHelper.callSwing UI fiber (R_callswing_envelope); otherwise the modal park
// throws IllegalStateException via UIFibers.checkInUIFiber()
// (D_gap_severity_triage loom-rescue contract).
//
// Icon dispatch (Path 2 internal): when no explicit Icon is passed,
// the messageType picks a VaadinIcon glyph. ERROR_MESSAGE → WARNING,
// INFORMATION_MESSAGE → INFO_CIRCLE, WARNING_MESSAGE → EXCLAMATION,
// QUESTION_MESSAGE → QUESTION_CIRCLE, PLAIN_MESSAGE → no icon. The
// UIManager.getIcon("OptionPane.errorIcon") public surface that lets
// migrators reach these glyphs by key is a separate (small) addition;
// not needed for the static-factory path which picks internally.

/** Emulator for {@link javax.swing.JOptionPane}. Emulator-only per SD_no_sjoptionpane — no surrogate. */
public class JOptionPane extends vaadinx.swing.JComponent implements javax.accessibility.Accessible {

    // ===========================================================
    // Constants — JDK-faithful values (re-declared because we don't
    // depend on javax.swing.JOptionPane at compile time).
    // ===========================================================

    public static final int ERROR_MESSAGE = 0;
    public static final int INFORMATION_MESSAGE = 1;
    public static final int WARNING_MESSAGE = 2;
    public static final int QUESTION_MESSAGE = 3;
    public static final int PLAIN_MESSAGE = -1;

    public static final int DEFAULT_OPTION = -1;
    public static final int YES_NO_OPTION = 0;
    public static final int YES_NO_CANCEL_OPTION = 1;
    public static final int OK_CANCEL_OPTION = 2;

    public static final int YES_OPTION = 0;
    public static final int NO_OPTION = 1;
    public static final int CANCEL_OPTION = 2;
    public static final int OK_OPTION = 0;
    public static final int CLOSED_OPTION = -1;

    public static final String ICON_PROPERTY = "icon";
    public static final String MESSAGE_PROPERTY = "message";
    public static final String VALUE_PROPERTY = "value";
    public static final String OPTIONS_PROPERTY = "options";
    public static final String INITIAL_VALUE_PROPERTY = "initialValue";
    public static final String MESSAGE_TYPE_PROPERTY = "messageType";
    public static final String OPTION_TYPE_PROPERTY = "optionType";
    public static final String SELECTION_VALUES_PROPERTY = "selectionValues";
    public static final String INITIAL_SELECTION_VALUE_PROPERTY = "initialSelectionValue";
    public static final String INPUT_VALUE_PROPERTY = "inputValue";
    public static final String WANTS_INPUT_PROPERTY = "wantsInput";

    /** JDK sentinel marking "no value chosen yet" — distinct from null (which is a legitimate user-set value). */
    public static final Object UNINITIALIZED_VALUE = "uninitializedValue";

    // ===========================================================
    // State (mirrors javax.swing.JOptionPane's own fields).
    // ===========================================================

    protected Object message;
    protected int messageType = PLAIN_MESSAGE;
    protected int optionType = DEFAULT_OPTION;
    protected vaadinx.swing.Icon icon;
    protected Object[] options;
    protected Object initialValue;
    protected Object value = UNINITIALIZED_VALUE;
    protected Object[] selectionValues;
    protected Object initialSelectionValue;
    protected Object inputValue = UNINITIALIZED_VALUE;
    protected boolean wantsInput;

    // The input control (JTextField or JComboBox) we plant inside the
    // pane when wantsInput == true — held so the OK button's click
    // handler can read the user's value without re-querying the DOM.
    private vaadinx.swing.JComponent inputComponent;

    // ===========================================================
    // Constructors — seven JDK signatures.
    //
    // Peer is a plain Div with flex-column layout; JOptionPane is a
    // leaf in javax.swing.* (no public subclasses), so R_leaf_peer_lockdown lock-down
    // applies — there is no protected (Component peer) ctor.
    // ===========================================================

    public JOptionPane() {
        this("JOptionPane message", PLAIN_MESSAGE, DEFAULT_OPTION, null, null, null);
    }

    public JOptionPane(Object message) {
        this(message, PLAIN_MESSAGE, DEFAULT_OPTION, null, null, null);
    }

    public JOptionPane(Object message, int messageType) {
        this(message, messageType, DEFAULT_OPTION, null, null, null);
    }

    public JOptionPane(Object message, int messageType, int optionType) {
        this(message, messageType, optionType, null, null, null);
    }

    public JOptionPane(Object message, int messageType, int optionType, vaadinx.swing.Icon icon) {
        this(message, messageType, optionType, icon, null, null);
    }

    public JOptionPane(Object message, int messageType, int optionType, vaadinx.swing.Icon icon, Object[] options) {
        this(message, messageType, optionType, icon, options, null);
    }

    public JOptionPane(Object message, int messageType, int optionType, vaadinx.swing.Icon icon,
                       Object[] options, Object initialValue) {
        super(new com.vaadin.flow.component.html.Div());
        this.message = message;
        this.messageType = checkMessageType(messageType);
        this.optionType = checkOptionType(optionType);
        this.icon = icon;
        this.options = options;
        this.initialValue = initialValue;
        // Default flex-column layout for the option-pane content. The
        // populated structure is icon-row → optional-input → button-row.
        getPeer().getElement().getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--vaadin-gap-m, 1em)")
                .set("padding", "var(--vaadin-padding-s, 0.5em)")
                .set("min-width", "20em");
    }

    private static int checkMessageType(int t) {
        if (t != ERROR_MESSAGE && t != INFORMATION_MESSAGE && t != WARNING_MESSAGE
                && t != QUESTION_MESSAGE && t != PLAIN_MESSAGE) {
            // A bare RuntimeException, matching javax.swing.JOptionPane exactly
            // (measured on JDK 25) rather than the IllegalArgumentException the
            // message shape suggests. R_match_swing_errors wants the type Swing throws.
            throw new RuntimeException(
                    "JOptionPane: type must be one of JOptionPane.ERROR_MESSAGE,"
                    + " JOptionPane.INFORMATION_MESSAGE, JOptionPane.WARNING_MESSAGE,"
                    + " JOptionPane.QUESTION_MESSAGE or JOptionPane.PLAIN_MESSAGE");
        }
        return t;
    }

    /**
     * @throws RuntimeException — a <em>bare</em> one, not the {@code IllegalArgumentException}
     *         the message shape suggests, because that is what {@code javax.swing.JOptionPane}
     *         throws here (measured on JDK 25). R_match_swing_errors wants the type Swing
     *         throws, and a migrated {@code catch (IllegalArgumentException)} that never
     *         fired on the desktop must keep not firing.
     */
    private static int checkOptionType(int t) {
        if (t != DEFAULT_OPTION && t != YES_NO_OPTION && t != YES_NO_CANCEL_OPTION
                && t != OK_CANCEL_OPTION) {
            throw new RuntimeException(
                    "JOptionPane: option type must be one of JOptionPane.DEFAULT_OPTION,"
                    + " JOptionPane.YES_NO_OPTION, JOptionPane.YES_NO_CANCEL_OPTION"
                    + " or JOptionPane.OK_CANCEL_OPTION");
        }
        return t;
    }

    // ===========================================================
    // Getters / setters with PCE on every transition.
    // ===========================================================

    public void setMessage(Object newMessage) {
        Object old = this.message;
        this.message = newMessage;
        firePropertyChange(MESSAGE_PROPERTY, old, newMessage);
    }

    public Object getMessage() { return message; }

    public void setIcon(vaadinx.swing.Icon newIcon) {
        vaadinx.swing.Icon old = this.icon;
        this.icon = newIcon;
        firePropertyChange(ICON_PROPERTY, old, newIcon);
    }

    public vaadinx.swing.Icon getIcon() { return icon; }

    public void setValue(Object newValue) {
        Object old = this.value;
        this.value = newValue;
        firePropertyChange(VALUE_PROPERTY, old, newValue);
    }

    public Object getValue() { return value; }

    public void setOptions(Object[] newOptions) {
        Object[] old = this.options;
        this.options = newOptions;
        firePropertyChange(OPTIONS_PROPERTY, old, newOptions);
    }

    public Object[] getOptions() { return options; }

    public void setInitialValue(Object newInitialValue) {
        Object old = this.initialValue;
        this.initialValue = newInitialValue;
        firePropertyChange(INITIAL_VALUE_PROPERTY, old, newInitialValue);
    }

    public Object getInitialValue() { return initialValue; }

    public void setMessageType(int newType) {
        int old = this.messageType;
        this.messageType = checkMessageType(newType);
        firePropertyChange(MESSAGE_TYPE_PROPERTY, old, newType);
    }

    public int getMessageType() { return messageType; }

    public void setOptionType(int newType) {
        int old = this.optionType;
        this.optionType = checkOptionType(newType);
        firePropertyChange(OPTION_TYPE_PROPERTY, old, newType);
    }

    public int getOptionType() { return optionType; }

    public void setSelectionValues(Object[] newValues) {
        Object[] old = this.selectionValues;
        this.selectionValues = newValues;
        // JDK side-effect: setSelectionValues forces wantsInput=true
        // when newValues != null. Migrated code that relies on the
        // showInputDialog-with-selection path needs this to trigger
        // the combo render.
        if (newValues != null && !wantsInput) {
            setWantsInput(true);
        }
        firePropertyChange(SELECTION_VALUES_PROPERTY, old, newValues);
    }

    public Object[] getSelectionValues() { return selectionValues; }

    public void setInitialSelectionValue(Object newValue) {
        Object old = this.initialSelectionValue;
        this.initialSelectionValue = newValue;
        firePropertyChange(INITIAL_SELECTION_VALUE_PROPERTY, old, newValue);
    }

    public Object getInitialSelectionValue() { return initialSelectionValue; }

    public void setInputValue(Object newValue) {
        Object old = this.inputValue;
        this.inputValue = newValue;
        firePropertyChange(INPUT_VALUE_PROPERTY, old, newValue);
    }

    public Object getInputValue() { return inputValue; }

    public void setWantsInput(boolean newValue) {
        boolean old = this.wantsInput;
        this.wantsInput = newValue;
        firePropertyChange(WANTS_INPUT_PROPERTY, old, newValue);
    }

    public boolean getWantsInput() { return wantsInput; }

    // ===========================================================
    // createDialog — package the pane into a modal JDialog ready
    // for a setVisible(true) blocking show.
    // ===========================================================

    /**
     * Build a modal {@link JDialog} hosting this pane, owned by the
     * window ancestor of {@code parentComponent} (or by no owner if
     * {@code parentComponent} is null / not in a window).
     *
     * <p>The returned dialog is configured per JDK contract:
     * APPLICATION_MODAL, DISPOSE_ON_CLOSE, non-resizable. Its content
     * pane carries this JOptionPane as its sole child; the pane's
     * children (icon row, optional input, button row) are rebuilt on
     * each {@code createDialog} call so reused panes pick up state
     * mutations between shows.
     */
    public JDialog createDialog(vaadinx.awt.Component parentComponent, String title) {
        vaadinx.awt.Window owner = getWindowAncestor(parentComponent);
        JDialog dialog;
        if (owner instanceof vaadinx.awt.Frame f) {
            dialog = new JDialog(f, title, true);
        } else if (owner instanceof vaadinx.awt.Dialog d) {
            dialog = new JDialog(d, title, true);
        } else {
            dialog = new JDialog((vaadinx.awt.Frame) null, title, true);
        }
        dialog.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        // Reset the value back to UNINITIALIZED_VALUE before building the
        // button handlers — per JDK contract, a pane reused across
        // shows starts each cycle un-chosen, and the X / outside-click
        // close path leaves UNINITIALIZED_VALUE on the pane (which we
        // translate to CLOSED_OPTION on return). User code that subclasses
        // JOptionPane and overrides createDialog can pre-populate value
        // if it wants to, in which case our reset is undesirable —
        // accept that as a future-edge-case rather than over-design.
        this.value = UNINITIALIZED_VALUE;
        this.inputValue = UNINITIALIZED_VALUE;
        populate(dialog);
        dialog.add(this);
        return dialog;
    }

    /**
     * Walk parent chain from the given component up to find the
     * enclosing {@link vaadinx.awt.Window}. Returns null if the
     * component is null or has no window ancestor.
     */
    private static vaadinx.awt.Window getWindowAncestor(vaadinx.awt.Component c) {
        while (c != null) {
            if (c instanceof vaadinx.awt.Window w) return w;
            c = c.getParent();
        }
        return null;
    }

    // ===========================================================
    // Internal: build the icon row + optional input + button row
    // into this pane's Div peer.
    // ===========================================================

    private void populate(JDialog dialog) {
        removeAll();
        inputComponent = null;

        // Top row: icon (if any) + message text. Lay out as flex row
        // so the icon sits beside the message, top-aligned.
        vaadinx.swing.JPanel header = new vaadinx.swing.JPanel();
        header.getPeer().getElement().getStyle()
                .set("display", "flex")
                .set("flex-direction", "row")
                .set("align-items", "flex-start")
                .set("gap", "var(--vaadin-gap-m, 1em)");

        com.vaadin.flow.component.Component iconComp = resolveIconComponent();
        if (iconComp != null) {
            // Plant the Vaadin icon directly on the JPanel's peer
            // element — JOptionPane's icon glyph is a leaf decoration,
            // not a JComponent the user will Karibu-lookup against.
            iconComp.getElement().getStyle().set("flex", "0 0 auto");
            header.getPeer().getElement().appendChild(iconComp.getElement());
        }

        if (message != null) {
            vaadinx.swing.JLabel msgLabel = new vaadinx.swing.JLabel(String.valueOf(message));
            msgLabel.getPeer().getElement().getStyle().set("flex", "1 1 auto");
            header.add(msgLabel);
        }
        add(header);

        // Optional input row.
        if (wantsInput) {
            if (selectionValues != null && selectionValues.length > 0) {
                vaadinx.swing.JComboBox<Object> combo = new vaadinx.swing.JComboBox<>(selectionValues);
                if (initialSelectionValue != null) {
                    combo.setSelectedItem(initialSelectionValue);
                }
                inputComponent = combo;
                add(combo);
            } else {
                vaadinx.swing.JTextField field = new vaadinx.swing.JTextField();
                if (initialSelectionValue != null) {
                    field.setText(String.valueOf(initialSelectionValue));
                }
                inputComponent = field;
                add(field);
            }
        }

        // Button row.
        Object[] btnLabels = resolveButtonOptions();
        Object firstButtonValue = (initialValue != null) ? initialValue
                : (btnLabels.length > 0 ? btnLabels[0] : null);

        vaadinx.swing.JPanel buttonRow = new vaadinx.swing.JPanel();
        buttonRow.getPeer().getElement().getStyle()
                .set("display", "flex")
                .set("flex-direction", "row")
                .set("justify-content", "flex-end")
                .set("gap", "var(--vaadin-gap-s, 0.5em)");

        for (Object opt : btnLabels) {
            vaadinx.swing.JButton btn = makeOptionButton(opt, dialog);
            buttonRow.add(btn);
            if (opt != null && opt.equals(firstButtonValue)) {
                // Best-effort: mark the initial button as default-capable
                // and request focus on it. Real focus-routing through
                // Karibu / browser is best-effort per R_layouts_close_enough; the visual
                // primary attribute is the load-bearing piece for
                // keyboard Enter activation under the JRootPane
                // default-button wiring (D_rootpane_holder).
                btn.getPeer().getElement().setAttribute("theme", "primary");
            }
        }
        add(buttonRow);
    }

    /**
     * Resolve the icon Vaadin component for this pane. If the user
     * passed an {@link vaadinx.swing.Icon} explicitly, use it via
     * {@link vaadinx.EHelper#toVaadinIconComponent} (Path 1); otherwise
     * pick a {@link com.vaadin.flow.component.icon.VaadinIcon} from
     * the message type (Path 2 internal). PLAIN_MESSAGE returns null
     * — no decorative glyph for plain text.
     */
    private com.vaadin.flow.component.Component resolveIconComponent() {
        if (icon != null) {
            return vaadinx.EHelper.toVaadinIconComponent("JOptionPane", icon);
        }
        com.vaadin.flow.component.icon.VaadinIcon glyph = vaadinIconForMessageType(messageType);
        if (glyph == null) return null;
        com.vaadin.flow.component.icon.Icon iconComp = glyph.create();
        // Tint by message type so the visual cue reads at a glance — matches the
        // spirit of Swing's L&F-supplied colored icons (BasicOptionPaneUI's
        // questionIcon / errorIcon / etc.).
        //
        // The class names the *semantic*; emul/emulator-theme.css picks the
        // colour. Not written inline, which is what the previous shape did:
        // an inline style outranks every stylesheet short of !important, so a
        // migrator could not re-tint this icon by any standard means
        // (D_theme_is_lookandfeel). A class is ordinary cascade.
        String cls = fillClassForMessageType(messageType);
        if (cls != null) {
            iconComp.getElement().getClassList().add(cls);
        }
        iconComp.getElement().getStyle().set("width", "2em").set("height", "2em");
        return iconComp;
    }

    private static com.vaadin.flow.component.icon.VaadinIcon vaadinIconForMessageType(int messageType) {
        return switch (messageType) {
            case ERROR_MESSAGE -> com.vaadin.flow.component.icon.VaadinIcon.EXCLAMATION_CIRCLE;
            case INFORMATION_MESSAGE -> com.vaadin.flow.component.icon.VaadinIcon.INFO_CIRCLE;
            case WARNING_MESSAGE -> com.vaadin.flow.component.icon.VaadinIcon.WARNING;
            case QUESTION_MESSAGE -> com.vaadin.flow.component.icon.VaadinIcon.QUESTION_CIRCLE;
            default -> null;
        };
    }

    /**
     * The CSS class carrying this message type's tint, resolved by
     * {@code emul/emulator-theme.css}.
     *
     * <p>A class rather than a colour because the base styles define <b>no
     * semantic status colours</b> — by design: Vaadin's own answer to "this is
     * an error" is a component <i>theme variant</i>, which {@code vaadin-icon}
     * does not offer. So the theme-name knowledge lives in one stylesheet with
     * an Aura → Lumo → literal fallback chain, rather than being spread across
     * Java call sites, and the migrator overrides one selector they already
     * know how to write.
     */
    private static String fillClassForMessageType(int messageType) {
        return switch (messageType) {
            case ERROR_MESSAGE -> "emul-msg-error";
            case WARNING_MESSAGE -> "emul-msg-warning";
            case INFORMATION_MESSAGE, QUESTION_MESSAGE -> "emul-msg-info";
            default -> null;
        };
    }

    /**
     * Resolve which button labels to render. User-supplied {@code options}
     * win; otherwise pick the JDK-default set for the option type.
     */
    private Object[] resolveButtonOptions() {
        if (options != null) return options;
        return switch (optionType) {
            case YES_NO_OPTION -> new Object[]{"Yes", "No"};
            case YES_NO_CANCEL_OPTION -> new Object[]{"Yes", "No", "Cancel"};
            case OK_CANCEL_OPTION -> new Object[]{"OK", "Cancel"};
            case DEFAULT_OPTION -> new Object[]{"OK"};
            default -> new Object[]{"OK"};
        };
    }

    /**
     * Build a single button for the given option. The button's click
     * handler captures {@code opt} as the chosen value, copies any
     * input-component value into {@code inputValue}, then disposes
     * the dialog (which releases the modal latch in
     * {@link vaadinx.awt.Dialog#parkUntilClose}).
     */
    private vaadinx.swing.JButton makeOptionButton(Object opt, JDialog dialog) {
        String label = opt instanceof javax.swing.Icon ? "" : String.valueOf(opt);
        vaadinx.swing.JButton btn = new vaadinx.swing.JButton(label);
        btn.addActionListener(e -> {
            // Capture the input value BEFORE setting value/disposing —
            // otherwise the setValue PCE listener path might race a
            // user-side handler that reads inputValue.
            captureInputValue();
            setValue(opt);
            dialog.dispose();
        });
        return btn;
    }

    private void captureInputValue() {
        if (!wantsInput || inputComponent == null) {
            return;
        }
        if (inputComponent instanceof vaadinx.swing.JTextField field) {
            setInputValue(field.getText());
        } else if (inputComponent instanceof vaadinx.swing.JComboBox<?> combo) {
            setInputValue(combo.getSelectedItem());
        }
    }

    public void selectInitialValue() {
        // Best-effort focus on the initial button. The button row was
        // marked with theme=primary in populate() for the initial
        // option; full requestFocus routing through Vaadin is R_layouts_close_enough best-
        // effort and lives on JComponent — the visual primary cue is
        // the load-bearing piece for keyboard Enter activation.
    }

    // ===========================================================
    // Static factories — message / confirm / input / option.
    //
    // Each builds a JOptionPane with the given state, hosts it on a
    // modal JDialog via createDialog, calls dialog.setVisible(true)
    // — which parks the calling VT on the modal latch (SD_modal_park) — and
    // translates the user's chosen button to the JDK return shape
    // (void / int / String / Object).
    // ===========================================================

    public static void showMessageDialog(vaadinx.awt.Component parent, Object message) {
        showMessageDialog(parent, message, "Message", INFORMATION_MESSAGE, null);
    }

    public static void showMessageDialog(vaadinx.awt.Component parent, Object message, String title, int messageType) {
        showMessageDialog(parent, message, title, messageType, null);
    }

    public static void showMessageDialog(vaadinx.awt.Component parent, Object message, String title,
                                         int messageType, vaadinx.swing.Icon icon) {
        showOptionDialog(parent, message, title, DEFAULT_OPTION, messageType, icon, null, null);
    }

    public static int showConfirmDialog(vaadinx.awt.Component parent, Object message) {
        return showConfirmDialog(parent, message, "Select an Option", YES_NO_CANCEL_OPTION, QUESTION_MESSAGE, null);
    }

    public static int showConfirmDialog(vaadinx.awt.Component parent, Object message, String title, int optionType) {
        return showConfirmDialog(parent, message, title, optionType, QUESTION_MESSAGE, null);
    }

    public static int showConfirmDialog(vaadinx.awt.Component parent, Object message, String title,
                                        int optionType, int messageType) {
        return showConfirmDialog(parent, message, title, optionType, messageType, null);
    }

    public static int showConfirmDialog(vaadinx.awt.Component parent, Object message, String title,
                                        int optionType, int messageType, vaadinx.swing.Icon icon) {
        return showOptionDialog(parent, message, title, optionType, messageType, icon, null, null);
    }

    public static String showInputDialog(Object message) {
        return showInputDialog(null, message);
    }

    public static String showInputDialog(Object message, Object initialSelectionValue) {
        return showInputDialog(null, message, initialSelectionValue);
    }

    public static String showInputDialog(vaadinx.awt.Component parent, Object message) {
        return showInputDialog(parent, message, null);
    }

    public static String showInputDialog(vaadinx.awt.Component parent, Object message, Object initialSelectionValue) {
        Object out = showInputDialogImpl(parent, message, "Input", QUESTION_MESSAGE, null, null, initialSelectionValue);
        return out == null ? null : String.valueOf(out);
    }

    public static String showInputDialog(vaadinx.awt.Component parent, Object message, String title, int messageType) {
        Object out = showInputDialogImpl(parent, message, title, messageType, null, null, null);
        return out == null ? null : String.valueOf(out);
    }

    public static Object showInputDialog(vaadinx.awt.Component parent, Object message, String title, int messageType,
                                         vaadinx.swing.Icon icon, Object[] selectionValues, Object initialSelectionValue) {
        return showInputDialogImpl(parent, message, title, messageType, icon, selectionValues, initialSelectionValue);
    }

    public static int showOptionDialog(vaadinx.awt.Component parent, Object message, String title,
                                       int optionType, int messageType, vaadinx.swing.Icon icon,
                                       Object[] options, Object initialValue) {
        JOptionPane pane = new JOptionPane(message, messageType, optionType, icon, options, initialValue);
        JDialog dialog = pane.createDialog(parent, title);
        dialog.setVisible(true); // parks the VT until user closes
        Object chosen = pane.getValue();
        return translateOptionToInt(chosen, optionType, options);
    }

    private static Object showInputDialogImpl(vaadinx.awt.Component parent, Object message, String title,
                                              int messageType, vaadinx.swing.Icon icon,
                                              Object[] selectionValues, Object initialSelectionValue) {
        JOptionPane pane = new JOptionPane(message, messageType, OK_CANCEL_OPTION, icon);
        pane.setWantsInput(true);
        pane.setSelectionValues(selectionValues);
        pane.setInitialSelectionValue(initialSelectionValue);
        JDialog dialog = pane.createDialog(parent, title);
        dialog.setVisible(true); // parks
        Object chosen = pane.getValue();
        // CLOSED_OPTION (X / outside-click) and Cancel both return null
        // per JDK contract — only OK propagates inputValue.
        if (chosen == null || chosen == UNINITIALIZED_VALUE) return null;
        if (chosen instanceof String s && ("Cancel".equals(s) || "OK".equals(s))) {
            if ("Cancel".equals(s)) return null;
            return pane.getInputValue() == UNINITIALIZED_VALUE ? null : pane.getInputValue();
        }
        // User-supplied options (rare for input dialog — we don't
        // surface that overload publicly) — return chosen as-is.
        return chosen;
    }

    /**
     * Translate the user-chosen value back to the int return code
     * that {@code showConfirmDialog} / {@code showOptionDialog}
     * promise. CLOSED_OPTION when the user dismissed via X /
     * outside-click; the option-array index when user-supplied
     * options are in play; the JDK constant matching the chosen
     * default-button label otherwise.
     */
    private static int translateOptionToInt(Object chosen, int optionType, Object[] userOptions) {
        if (chosen == null || chosen == UNINITIALIZED_VALUE) {
            return CLOSED_OPTION;
        }
        if (userOptions != null) {
            for (int i = 0; i < userOptions.length; i++) {
                if (chosen.equals(userOptions[i])) return i;
            }
            return CLOSED_OPTION;
        }
        // Default button labels — match resolveButtonOptions().
        return switch (String.valueOf(chosen)) {
            case "Yes", "OK" -> YES_OPTION; // YES_OPTION == OK_OPTION == 0
            case "No" -> NO_OPTION;
            case "Cancel" -> CANCEL_OPTION;
            default -> CLOSED_OPTION;
        };
    }

    // ===========================================================
    // Internal-frame variants (JInternalFrame).
    // Stub via onUnimplemented so migrated code that calls them
    // gets a WARN + best-effort fallback to the Dialog-shape factory.
    // ===========================================================

    public static void showInternalMessageDialog(vaadinx.awt.Component parent, Object message) {
        vaadinx.EHelper.onUnimplemented("JOptionPane", "showInternalMessageDialog", message);
        showMessageDialog(parent, message);
    }

    public static int showInternalConfirmDialog(vaadinx.awt.Component parent, Object message) {
        vaadinx.EHelper.onUnimplemented("JOptionPane", "showInternalConfirmDialog", message);
        return showConfirmDialog(parent, message);
    }

    public static String showInternalInputDialog(vaadinx.awt.Component parent, Object message) {
        vaadinx.EHelper.onUnimplemented("JOptionPane", "showInternalInputDialog", message);
        return showInputDialog(parent, message);
    }

    public static int showInternalOptionDialog(vaadinx.awt.Component parent, Object message, String title,
                                               int optionType, int messageType, vaadinx.swing.Icon icon,
                                               Object[] options, Object initialValue) {
        vaadinx.EHelper.onUnimplemented("JOptionPane", "showInternalOptionDialog", message);
        return showOptionDialog(parent, message, title, optionType, messageType, icon, options, initialValue);
    }

    // ===========================================================
    // createInternalFrame (JInternalFrame); stub.
    // ===========================================================

    public Object createInternalFrame(vaadinx.awt.Component parent, String title) {
        vaadinx.EHelper.onUnimplemented("JOptionPane", "createInternalFrame", parent, title);
        return null;
    }

    // ===========================================================
    // getRootFrame — JDK helper used by null-parent shows. Returns
    // the null-frame placeholder per JDK contract.
    // ===========================================================

    public static vaadinx.awt.Frame getRootFrame() {
        // JDK returns SwingUtilities.getSharedOwnerFrame(). We don't
        // model a singleton shared-owner frame; null is a legitimate
        // owner for JDialog (its (Frame) null ctor branch handles it),
        // and the static factories already pass null through.
        return null;
    }

    // ===========================================================
    // accessibility / paramString
    // ===========================================================

    public javax.accessibility.AccessibleContext getAccessibleContext() {
        vaadinx.EHelper.onUnimplemented("JOptionPane", "getAccessibleContext");
        return null;
    }

    public String getUIClassID() {
        return "OptionPaneUI";
    }

    @Override
    protected String paramString() {
        return super.paramString() + ",messageType=" + messageType + ",optionType=" + optionType
                + ",wantsInput=" + wantsInput;
    }
}
