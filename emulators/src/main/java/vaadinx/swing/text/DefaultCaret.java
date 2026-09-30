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
 * This file is derived from OpenJDK's javax.swing.text.DefaultCaret
 * (https://github.com/openjdk/jdk, tag jdk-25-ga) and was modified by
 * Vaadin Ltd in 2026; see PROVENANCE.md at the root of
 * https://github.com/vaadin/swingbridge-emulators for how and why, and the
 * repository history for each change. Vaadin Ltd licenses its modifications
 * under the same GNU General Public License version 2 only and extends the
 * "Classpath" exception, as set out in the LICENSE file that accompanied
 * this code, to its version of this file.
 */

package vaadinx.swing.text;

// Hand-finished emulator. The model half of the JDK's DefaultCaret — dot, mark,
// the change fan-out, the NavigationFilter hand-off, and the Document tracking
// with its update policy — as the JDK's own bodies. The paint half (blink timer,
// highlight painting, mouse positioning) is the browser's. The owning
// JTextComponent renders dot/mark onto the peer's input on every change and feeds
// the browser's own caret moves back in through setDot / moveDot. Decision:
// D_emulator_caret.

/**
 * Emulator for {@link javax.swing.text.DefaultCaret} — the caret every text
 * component installs, and the one its caret methods read and write:
 *
 * <pre>{@code
 * DefaultCaret caret = (DefaultCaret) logArea.getCaret();
 * caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);   // follow appends from a worker too
 * logArea.getCaret().setDot(logArea.getDocument().getLength());
 * }</pre>
 *
 * <p>Dot and mark follow the component's Document as the JDK's do: under the default
 * {@link #UPDATE_WHEN_ON_EDT} an edit on the UI thread moves them, an edit from a
 * background thread only clamps them. A {@link javax.swing.text.NavigationFilter}
 * installed on the component sees every {@code setDot} / {@code moveDot}, the user's
 * clicks and arrow keys included, since those arrive from the browser through the same
 * two methods.
 *
 * <p>The caret registers no focus or mouse listener on its component, unlike the JDK's:
 * each would install a peer bridge costing a round trip per focus change or click, and
 * the browser already places the caret. So {@link #focusGained} and the mouse callbacks
 * run only when called, {@link #isSelectionVisible()} stays {@code false}, and
 * {@link #paint} and the blink timer do nothing.
 */
public class DefaultCaret extends java.awt.Rectangle implements javax.swing.text.Caret,
        vaadinx.awt.event.FocusListener, vaadinx.awt.event.MouseListener, vaadinx.awt.event.MouseMotionListener {

    /** Moves the caret on an edit made on the UI thread; clamps it on any other. The default. */
    public static final int UPDATE_WHEN_ON_EDT = 0;

    /** Never moves the caret on an edit, only clamps it to the Document. */
    public static final int NEVER_UPDATE = 1;

    /** Moves the caret on every edit, whichever thread made it. */
    public static final int ALWAYS_UPDATE = 2;

    protected javax.swing.event.EventListenerList listenerList = new javax.swing.event.EventListenerList();

    protected transient javax.swing.event.ChangeEvent changeEvent = null;

    JTextComponent component;

    int updatePolicy = UPDATE_WHEN_ON_EDT;
    boolean visible;
    boolean active;
    int dot;
    int mark;
    // The JDK's selectionTag is the Highlighter's handle on a painted selection; only
    // whether one is shown is observable, through handleSetDot's change test.
    boolean selectionHighlighted;
    boolean selectionVisible;
    // The JDK's flasher Timer, reduced to its delay: 0 is "no flasher".
    int flasherDelay;
    java.awt.Point magicCaretPosition;
    transient javax.swing.text.Position.Bias dotBias;
    transient javax.swing.text.Position.Bias markBias;
    transient ModelHandler handler = new ModelHandler();
    private transient javax.swing.text.NavigationFilter.FilterBypass filterBypass;
    private int savedBlinkRate = 0;
    private boolean isBlinkRateSaved = false;
    private boolean forceCaretPositionChange;

    public DefaultCaret() {
    }

    public void setUpdatePolicy(int policy) {
        updatePolicy = policy;
    }

    public int getUpdatePolicy() {
        return updatePolicy;
    }

    protected final JTextComponent getComponent() {
        return component;
    }

    protected final synchronized void repaint() {
        if (component != null) {
            component.repaint(x, y, width, height);
        }
    }

    /** Paints nothing: the browser draws the caret (R_match_swing_errors sub-bucket (b)). */
    protected synchronized void damage(java.awt.Rectangle r) {
        vaadinx.EHelper.onNoop("DefaultCaret", "damage");
    }

    /** Scrolls nothing: the browser keeps its own caret in view. */
    protected void adjustVisibility(java.awt.Rectangle nloc) {
        vaadinx.EHelper.onNoop("DefaultCaret", "adjustVisibility");
    }

    /**
     * WARNs and moves nothing: mapping a mouse point to an offset needs the view
     * geometry only the browser has. A browser click reaches {@link #setDot} directly.
     */
    protected void positionCaret(vaadinx.awt.event.MouseEvent e) {
        vaadinx.EHelper.onUnimplemented("DefaultCaret", "positionCaret", e);
    }

    /** WARNs and moves nothing; a browser drag-select reaches {@link #moveDot} directly. */
    protected void moveCaret(vaadinx.awt.event.MouseEvent e) {
        vaadinx.EHelper.onUnimplemented("DefaultCaret", "moveCaret", e);
    }

    public void focusGained(vaadinx.awt.event.FocusEvent e) {
        if (component.isEnabled()) {
            if (component.isEditable()) {
                if (isBlinkRateSaved) {
                    setBlinkRate(savedBlinkRate);
                    savedBlinkRate = 0;
                    isBlinkRateSaved = false;
                }
            } else {
                if (getBlinkRate() != 0) {
                    if (!isBlinkRateSaved) {
                        savedBlinkRate = getBlinkRate();
                        isBlinkRateSaved = true;
                    }
                    setBlinkRate(0);
                }
            }
            setVisible(true);
            setSelectionVisible(true);
        }
    }

    public void focusLost(vaadinx.awt.event.FocusEvent e) {
        setVisible(false);
        // The JDK keeps the selection shown when it owns the system selection, which
        // needs a platform selection clipboard a browser does not expose.
        setSelectionVisible((e.getCause() == vaadinx.awt.event.FocusEvent.Cause.ACTIVATION
                || e.getOppositeComponent() instanceof vaadinx.swing.JRootPane) && e.isTemporary());
    }

    public void mouseClicked(vaadinx.awt.event.MouseEvent e) {
        vaadinx.EHelper.onUnimplemented("DefaultCaret", "mouseClicked", e);
    }

    public void mousePressed(vaadinx.awt.event.MouseEvent e) {
        vaadinx.EHelper.onUnimplemented("DefaultCaret", "mousePressed", e);
    }

    public void mouseReleased(vaadinx.awt.event.MouseEvent e) {
        vaadinx.EHelper.onUnimplemented("DefaultCaret", "mouseReleased", e);
    }

    public void mouseEntered(vaadinx.awt.event.MouseEvent e) {
    }

    public void mouseExited(vaadinx.awt.event.MouseEvent e) {
    }

    public void mouseDragged(vaadinx.awt.event.MouseEvent e) {
        vaadinx.EHelper.onUnimplemented("DefaultCaret", "mouseDragged", e);
    }

    public void mouseMoved(vaadinx.awt.event.MouseEvent e) {
    }

    /** Paints nothing: user-authored {@code Graphics} paint is out of scope (R_match_swing_errors sub-bucket (b)). */
    public void paint(java.awt.Graphics g) {
        vaadinx.EHelper.onUnimplemented("DefaultCaret", "paint", g);
    }

    /**
     * Installs this caret on {@code c}: dot and mark reset to 0, and the caret starts
     * following {@code c}'s Document and its {@code "document"} property.
     * {@link JTextComponent#setCaret} calls it.
     */
    public void install(JTextComponent c) {
        component = c;
        javax.swing.text.Document doc = c.getDocument();
        dot = mark = 0;
        dotBias = markBias = javax.swing.text.Position.Bias.Forward;
        if (doc != null) {
            doc.addDocumentListener(handler);
        }
        c.addPropertyChangeListener(handler);
        c.caretListenersChanged();
    }

    public void deinstall(JTextComponent c) {
        c.removePropertyChangeListener(handler);
        javax.swing.text.Document doc = c.getDocument();
        if (doc != null) {
            doc.removeDocumentListener(handler);
        }
        synchronized (this) {
            component = null;
        }
    }

    /**
     * WARNs and installs nothing. The {@link javax.swing.text.Caret} interface asks for
     * the JDK's {@code JTextComponent}, which a migrated app never holds;
     * {@link #install(JTextComponent)} is the one {@code setCaret} calls.
     */
    @Override
    public void install(javax.swing.text.JTextComponent c) {
        vaadinx.EHelper.onUnimplemented("DefaultCaret", "install(javax.swing.text.JTextComponent)", c);
    }

    /** WARNs and deinstalls nothing — see {@link #install(javax.swing.text.JTextComponent)}. */
    @Override
    public void deinstall(javax.swing.text.JTextComponent c) {
        vaadinx.EHelper.onUnimplemented("DefaultCaret", "deinstall(javax.swing.text.JTextComponent)", c);
    }

    public void addChangeListener(javax.swing.event.ChangeListener l) {
        listenerList.add(javax.swing.event.ChangeListener.class, l);
        if (component != null) component.caretListenersChanged();
    }

    public void removeChangeListener(javax.swing.event.ChangeListener l) {
        listenerList.remove(javax.swing.event.ChangeListener.class, l);
    }

    public javax.swing.event.ChangeListener[] getChangeListeners() {
        return listenerList.getListeners(javax.swing.event.ChangeListener.class);
    }

    /** Notifies the change listeners, last registered first, as the JDK does. */
    protected void fireStateChanged() {
        Object[] listeners = listenerList.getListenerList();
        for (int i = listeners.length - 2; i >= 0; i -= 2) {
            if (listeners[i] == javax.swing.event.ChangeListener.class) {
                if (changeEvent == null)
                    changeEvent = new javax.swing.event.ChangeEvent(this);
                ((javax.swing.event.ChangeListener) listeners[i + 1]).stateChanged(changeEvent);
            }
        }
    }

    public <T extends java.util.EventListener> T[] getListeners(Class<T> listenerType) {
        return listenerList.getListeners(listenerType);
    }

    public void setSelectionVisible(boolean vis) {
        if (vis != selectionVisible) {
            selectionVisible = vis;
            if (selectionVisible) {
                // The JDK asks the component for its Highlighter here, so a caret nothing
                // installed NPEs as it does there. The browser paints the selection
                // whichever highlighter is set.
                component.getHighlighter();
                if (dot != mark) {
                    selectionHighlighted = true;
                }
            } else {
                selectionHighlighted = false;
            }
        }
    }

    public boolean isSelectionVisible() {
        return selectionVisible;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean e) {
        active = e;
        if (component != null) {
            if (visible != e) {
                visible = e;
            }
        }
    }

    /**
     * Stores the rate; nothing blinks, since the browser draws the caret.
     *
     * @throws IllegalArgumentException on a negative rate, as in the JDK
     */
    public void setBlinkRate(int rate) {
        if (rate < 0) {
            throw new IllegalArgumentException("Invalid blink rate: " + rate);
        }
        if (rate != 0) {
            if (component != null && component.isEditable()) {
                flasherDelay = rate;
            } else {
                savedBlinkRate = rate;
                isBlinkRateSaved = true;
            }
        } else {
            flasherDelay = 0;
            if ((component == null || component.isEditable()) && isBlinkRateSaved) {
                savedBlinkRate = 0;
                isBlinkRateSaved = false;
            }
        }
    }

    public int getBlinkRate() {
        if (isBlinkRateSaved) {
            return savedBlinkRate;
        }
        return flasherDelay;
    }

    public int getDot() {
        return dot;
    }

    public int getMark() {
        return mark;
    }

    public void setDot(int dot) {
        setDot(dot, javax.swing.text.Position.Bias.Forward);
    }

    public void moveDot(int dot) {
        moveDot(dot, javax.swing.text.Position.Bias.Forward);
    }

    /**
     * Moves the dot, extending the selection from the mark. Unlike
     * {@link JTextComponent#moveCaretPosition}, it does not clamp: an offset past the
     * Document's end is stored, as the JDK's is.
     *
     * @throws IllegalArgumentException if {@code dotBias} is null
     */
    public void moveDot(int dot, javax.swing.text.Position.Bias dotBias) {
        if (dotBias == null) {
            throw new IllegalArgumentException("null bias");
        }

        if (!component.isEnabled()) {
            // don't allow selection on disabled components.
            setDot(dot, dotBias);
            return;
        }
        if (dot != this.dot) {
            javax.swing.text.NavigationFilter filter = component.getNavigationFilter();

            if (filter != null) {
                filter.moveDot(getFilterBypass(), dot, dotBias);
            } else {
                handleMoveDot(dot, dotBias);
            }
        }
    }

    void handleMoveDot(int dot, javax.swing.text.Position.Bias dotBias) {
        changeCaretPosition(dot, dotBias);

        if (selectionVisible) {
            selectionHighlighted = Math.min(dot, mark) != Math.max(dot, mark);
        }
    }

    /**
     * Collapses the selection onto {@code dot}, clamped to the Document.
     *
     * @throws IllegalArgumentException if {@code dotBias} is null
     */
    public void setDot(int dot, javax.swing.text.Position.Bias dotBias) {
        if (dotBias == null) {
            throw new IllegalArgumentException("null bias");
        }

        javax.swing.text.NavigationFilter filter = component.getNavigationFilter();

        if (filter != null) {
            filter.setDot(getFilterBypass(), dot, dotBias);
        } else {
            handleSetDot(dot, dotBias);
        }
    }

    void handleSetDot(int dot, javax.swing.text.Position.Bias dotBias) {
        // move dot, if it changed
        javax.swing.text.Document doc = component.getDocument();
        if (doc != null) {
            dot = Math.min(dot, doc.getLength());
        }
        dot = Math.max(dot, 0);

        // The position (0,Backward) is out of range so disallow it.
        if (dot == 0)
            dotBias = javax.swing.text.Position.Bias.Forward;

        mark = dot;
        if (this.dot != dot || this.dotBias != dotBias
                || selectionHighlighted || forceCaretPositionChange) {
            changeCaretPosition(dot, dotBias);
        }
        this.markBias = this.dotBias;
        selectionHighlighted = false;
    }

    public javax.swing.text.Position.Bias getDotBias() {
        return dotBias;
    }

    public javax.swing.text.Position.Bias getMarkBias() {
        return markBias;
    }

    /**
     * The JDK's bias guess with the bidi half dropped, since the Document's run
     * direction is not modelled: only the newline rule, which keeps the caret on the
     * line it was on.
     */
    javax.swing.text.Position.Bias guessBiasForOffset(int offset, javax.swing.text.Position.Bias lastBias) {
        if (lastBias == javax.swing.text.Position.Bias.Backward && offset > 0) {
            try {
                javax.swing.text.Segment s = new javax.swing.text.Segment();
                component.getDocument().getText(offset - 1, 1, s);
                if (s.count > 0 && s.array[s.offset] == '\n') {
                    lastBias = javax.swing.text.Position.Bias.Forward;
                }
            } catch (javax.swing.text.BadLocationException ble) {
            }
        }
        return lastBias;
    }

    /** Sets the dot and notifies; the owning component renders the move onto its peer. */
    void changeCaretPosition(int dot, javax.swing.text.Position.Bias dotBias) {
        this.dot = dot;
        this.dotBias = dotBias;
        fireStateChanged();

        setMagicCaretPosition(null);
    }

    private void ensureValidPosition() {
        int length = component.getDocument().getLength();
        if (dot > length || mark > length) {
            // Current location is bogus and filter likely vetoed the
            // change, force the reset without giving the filter a
            // chance at changing it.
            handleSetDot(length, javax.swing.text.Position.Bias.Forward);
        }
    }

    public void setMagicCaretPosition(java.awt.Point p) {
        magicCaretPosition = p;
    }

    public java.awt.Point getMagicCaretPosition() {
        return magicCaretPosition;
    }

    public boolean equals(Object obj) {
        return (this == obj);
    }

    public String toString() {
        String s = "Dot=(" + dot + ", " + dotBias + ")";
        s += " Mark=(" + mark + ", " + markBias + ")";
        return s;
    }

    private javax.swing.text.NavigationFilter.FilterBypass getFilterBypass() {
        if (filterBypass == null) {
            filterBypass = new CaretFilterBypass();
        }
        return filterBypass;
    }

    private boolean followsEdit() {
        return getUpdatePolicy() == ALWAYS_UPDATE
                || (getUpdatePolicy() == UPDATE_WHEN_ON_EDT && vaadinx.swing.SwingUtilities.isEventDispatchThread());
    }

    /** The package-private {@code AbstractDocument.UndoRedoDocumentEvent}, recognised by name. */
    private static boolean isUndoRedo(javax.swing.event.DocumentEvent e) {
        return e.getClass().getName().equals("javax.swing.text.AbstractDocument$UndoRedoDocumentEvent");
    }

    /** The JDK's {@code Handler}, less the blink timer and the system-selection clipboard. */
    class ModelHandler implements java.beans.PropertyChangeListener, javax.swing.event.DocumentListener {

        public void insertUpdate(javax.swing.event.DocumentEvent e) {
            if (!followsEdit()) {
                return;
            }
            int offset = e.getOffset();
            int length = e.getLength();
            int newDot = dot;
            short changed = 0;

            if (isUndoRedo(e)) {
                setDot(offset + length);
                return;
            }
            if (newDot >= offset) {
                newDot += length;
                changed |= 1;
            }
            int newMark = mark;
            if (newMark >= offset) {
                newMark += length;
                changed |= 2;
            }

            if (changed != 0) {
                javax.swing.text.Position.Bias dotBias = DefaultCaret.this.dotBias;
                if (dot == offset) {
                    javax.swing.text.Document doc = component.getDocument();
                    boolean isNewline;
                    try {
                        javax.swing.text.Segment s = new javax.swing.text.Segment();
                        doc.getText(newDot - 1, 1, s);
                        isNewline = (s.count > 0 && s.array[s.offset] == '\n');
                    } catch (javax.swing.text.BadLocationException ble) {
                        isNewline = false;
                    }
                    if (isNewline) {
                        dotBias = javax.swing.text.Position.Bias.Forward;
                    } else {
                        dotBias = javax.swing.text.Position.Bias.Backward;
                    }
                }
                if (newMark == newDot) {
                    setDot(newDot, dotBias);
                    ensureValidPosition();
                } else {
                    setDot(newMark, markBias);
                    if (getDot() == newMark) {
                        // Due this test in case the filter vetoed the
                        // change in which case this probably won't be
                        // valid either.
                        moveDot(newDot, dotBias);
                    }
                    ensureValidPosition();
                }
            }
        }

        public void removeUpdate(javax.swing.event.DocumentEvent e) {
            if (!followsEdit()) {
                int length = component.getDocument().getLength();
                dot = Math.min(dot, length);
                mark = Math.min(mark, length);
                return;
            }
            int offs0 = e.getOffset();
            int offs1 = offs0 + e.getLength();
            int newDot = dot;
            boolean adjustDotBias = false;
            int newMark = mark;
            boolean adjustMarkBias = false;

            if (isUndoRedo(e)) {
                setDot(offs0);
                return;
            }
            if (newDot >= offs1) {
                newDot -= (offs1 - offs0);
                if (newDot == offs1) {
                    adjustDotBias = true;
                }
            } else if (newDot >= offs0) {
                newDot = offs0;
                adjustDotBias = true;
            }
            if (newMark >= offs1) {
                newMark -= (offs1 - offs0);
                if (newMark == offs1) {
                    adjustMarkBias = true;
                }
            } else if (newMark >= offs0) {
                newMark = offs0;
                adjustMarkBias = true;
            }
            if (newMark == newDot) {
                forceCaretPositionChange = true;
                try {
                    setDot(newDot, guessBiasForOffset(newDot, dotBias));
                } finally {
                    forceCaretPositionChange = false;
                }
                ensureValidPosition();
            } else {
                javax.swing.text.Position.Bias dotBias = DefaultCaret.this.dotBias;
                javax.swing.text.Position.Bias markBias = DefaultCaret.this.markBias;
                if (adjustDotBias) {
                    dotBias = guessBiasForOffset(newDot, dotBias);
                }
                if (adjustMarkBias) {
                    markBias = guessBiasForOffset(mark, markBias);
                }
                setDot(newMark, markBias);
                if (getDot() == newMark) {
                    // Due this test in case the filter vetoed the change
                    // in which case this probably won't be valid either.
                    moveDot(newDot, dotBias);
                }
                ensureValidPosition();
            }
        }

        public void changedUpdate(javax.swing.event.DocumentEvent e) {
            if (!followsEdit()) {
                return;
            }
            if (isUndoRedo(e)) {
                setDot(e.getOffset() + e.getLength());
            }
        }

        /** Follows a {@code setDocument}: back to 0, and onto the new Document. */
        public void propertyChange(java.beans.PropertyChangeEvent evt) {
            Object oldValue = evt.getOldValue();
            Object newValue = evt.getNewValue();
            if ((oldValue instanceof javax.swing.text.Document) || (newValue instanceof javax.swing.text.Document)) {
                setDot(0);
                if (oldValue != null) {
                    ((javax.swing.text.Document) oldValue).removeDocumentListener(this);
                }
                if (newValue != null) {
                    ((javax.swing.text.Document) newValue).addDocumentListener(this);
                }
            } else if ("enabled".equals(evt.getPropertyName())) {
                Boolean enabled = (Boolean) evt.getNewValue();
                if (component.isFocusOwner()) {
                    if (enabled == Boolean.TRUE) {
                        if (component.isEditable()) {
                            setVisible(true);
                        }
                        setSelectionVisible(true);
                    } else {
                        setVisible(false);
                        setSelectionVisible(false);
                    }
                }
            }
        }
    }

    private class CaretFilterBypass extends javax.swing.text.NavigationFilter.FilterBypass {
        public javax.swing.text.Caret getCaret() {
            return DefaultCaret.this;
        }

        public void setDot(int dot, javax.swing.text.Position.Bias bias) {
            handleSetDot(dot, bias);
        }

        public void moveDot(int dot, javax.swing.text.Position.Bias bias) {
            handleMoveDot(dot, bias);
        }
    }
}
