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
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.ShortcutRegistration;
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.function.SerializablePredicate;
import com.vaadin.swingbridge.surrogates.internal.PceSupport;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;

import java.awt.IllegalComponentStateException;

/**
 * Surrogate for {@link javax.swing.JRootPane} per SD_sjframe. Extends
 * {@link Div} (so it carries {@link JComponentMixin}'s client-property /
 * border / PCE surface and participates in {@link com.vaadin.swingbridge.surrogates.internal.Registrations}
 * lookups) and holds the root-pane state JDK migrations expect:
 * content / layered / glass pane references plus the default-button
 * wiring with the Enter-key shortcut install.
 *
 * <h2>Structural, and the containment is the JDK's</h2>
 *
 * The Div base <em>is</em> the containment. A root pane holds
 * {glassPane, layeredPane}, and the <em>layered pane</em> holds
 * {menuBar, contentPane} — the JDK's own shape, which its class doc states
 * outright: <i>"(The JLayeredPane manages the menuBar and the contentPane.)"</i>
 * Both of the layered pane's children go into the same
 * {@code FRAME_CONTENT_LAYER}, so the layered pane is not stacking them in z;
 * {@code RootLayout} stacks them in y, reaching through the layered pane to do
 * it. That y-stacking is reproduced as CSS — root pane a flex column, layered
 * pane {@code display: contents}, bar {@code flex: 0 0 auto}, content pane
 * {@code flex: 1 1 auto} — so the pane a migrator reaches through is the pane
 * that renders. Classes and measurements live in {@code emul/swindow.css}.
 *
 * <p>Panes are created lazily, on first read, and attached as they are created;
 * a root pane nobody reaches through allocates nothing. The JDK builds all three
 * in its constructor, which the <em>emulator</em> layer reproduces by pushing
 * its own AWT-typed panes' peers down through these setters before any getter
 * runs (the two-layer typed-holder split, as with SJSlider's model). So the JDK
 * call order is faithful where it is observable, without every surrogate window
 * paying for three Divs it never shows.
 *
 * <h2>Default-button Enter shortcut (only browser-side concern)</h2>
 *
 * {@link #setDefaultButton(Button)} installs a Vaadin {@link Shortcuts}
 * registration: Enter anywhere in the UI fires the button's click, unless the
 * focused component claims that Enter for itself — a text area's newline, a
 * formatted field's commit ({@link EnterClaims}). The Button itself is the lifecycle owner — detach or GC removes the
 * shortcut. Replacing or clearing the default button tears down the
 * previous registration before wiring the new one so Enter doesn't
 * briefly double-trigger during the swap. The click body funnels
 * through {@link SHelper#callSwing} per R_callswing_envelope.
 *
 * <h2>What the layered and glass panes do and do not do</h2>
 *
 * Both are structural — they render, and the glass pane curtains. What stays
 * unimplemented is the JDK's <em>z-order</em> use of the layered pane: layer
 * constants round-trip but do not reorder anything, since Vaadin renders in DOM
 * order and the components that would need real layering
 * ({@code JInternalFrame}) render as Dialog overlays instead per D_internal_frames. The glass
 * pane's one accepted gap is keyboard focus: it swallows mouse input but tab
 * still reaches components behind it, exactly as real Swing's does unless the
 * app also grabs focus (D_glasspane_structural/SD_glasspane_structural).
 *
 * <h2>Standalone use</h2>
 *
 * A pure-surrogate caller can {@code new SJRootPane()} directly and it now
 * renders: the panes attach themselves as they are lazily created, so adding the
 * root pane to any Vaadin container gives a working root-pane-alike. This
 * retires SD_sjframe's pure-surrogate-rendering limitation. The one thing a standalone
 * pane cannot do is size itself against a window body — the
 * {@code emul-has-contentpane} half of the chain is applied by whoever owns the
 * host (see {@link SHelper#markContentPaneSpan}).
 */
// The same stylesheet SWindow declares, and declared here too because the root
// pane is the one element on *every* window path. An inline @MainWindow JFrame
// peers on an SJPanel, so no SWindow is ever instantiated in that UI — measured
// in the browser, .emul-rootpane computed `display: block` on the Sampler shell
// until some dialog happened to open and pull swindow.css in. Vaadin de-duplicates
// the <link>, so declaring it twice costs nothing.
@com.vaadin.flow.component.dependency.StyleSheet("emul/swindow.css")
public class SJRootPane extends Div implements JComponentMixin {

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

    // --- content / layered / glass pane holders ----------------------

    /**
     * The layered pane's child, lazily created by {@link #getContentPane()}.
     * The emulator seeds it instead, via {@link #setContentPane(Component)}.
     */
    private Component contentPane;

    /** This pane's second child; see {@link #getLayeredPane()}. */
    private Component layeredPane;

    /** This pane's first child — the JDK's index too. See {@link #getGlassPane()}. */
    private Component glassPane;

    /** The layered pane's other child, planted before {@link #contentPane}. */
    private SJMenuBar menuBar;

    // --- default-button + Enter shortcut -----------------------------

    /**
     * Installed default button; null means "no button currently captures
     * Enter." Typed {@link Button} (Vaadin) rather than
     * a surrogate type, so any button surrogate can be passed: {@code SJButton} and
     * {@code SButton} both IS-A Button.
     */
    private Button defaultButton;

    /**
     * Paired lifecycle with {@link #defaultButton} — removed when the
     * button is replaced / cleared so Enter doesn't fire the old click
     * after a swap.
     */
    private ShortcutRegistration defaultButtonShortcut;

    /** @see #setLayerEnterClaims */
    private SerializablePredicate<Component> layerEnterClaims = c -> false;

    /**
     * Place {@code child} under {@code parent} at {@code index}, unless it is
     * already parented there.
     *
     * <p>The guard is load-bearing, not an optimization. The emulator layer
     * plants its own AWT-typed panes as children of its {@code JRootPane}, which
     * nests their peers through {@code Container.addImpl}, and only then pushes
     * the references down here — so by the time these setters run, the element is
     * usually already in the right place, and re-inserting it would be a
     * remove-then-add that leaves {@code Container}'s own child list and the DOM
     * disagreeing about the count.
     */
    private static void plant(com.vaadin.flow.dom.Element parent,
                              com.vaadin.flow.dom.Element child, int index) {
        if (parent.equals(child.getParent())) return;
        parent.insertChild(index, child);
    }

    public SJRootPane() {
        _installSwingClass();
        // The one eager step: the flex-column / positioned-ancestor rule has to
        // be on the element before anything is planted inside it. Panes stay
        // lazy — see the class javadoc on why that is faithful anyway.
        SHelper.markRootPane(this);
    }

    // --- content pane accessors --------------------------------------

    /**
     * The content pane, lazy-created and planted inside the layered pane on
     * first read (which lazily creates that too) — JDK's "lazy default content
     * pane" contract, made structural.
     */
    public Component getContentPane() {
        if (contentPane == null) setContentPane(createContentPane());
        return contentPane;
    }

    /**
     * Plant {@code newPane} as the layered pane's content child. JDK contract:
     * null throws {@link IllegalComponentStateException}. Replacement does NOT
     * migrate children (same as {@code JRootPane.setContentPane}) — callers that
     * want to keep content must re-add to the new pane.
     *
     * <p>Planted at the <em>end</em> of the layered pane, so a menu bar already
     * present stays above it: the CSS column takes its order from the DOM, where
     * {@code RootLayout} takes it from the {@code menuBar} field.
     */
    public void setContentPane(Component newPane) {
        if (newPane == null) {
            throw new IllegalComponentStateException("contentPane cannot be set to null");
        }
        if (newPane == this.contentPane) return;
        Component old = this.contentPane;
        if (old != null) old.getElement().removeFromParent();
        plant(getLayeredPane().getElement(), newPane.getElement(),
                getLayeredPane().getElement().getChildCount());
        newPane.getElement().getClassList().add("emul-contentpane");
        this.contentPane = newPane;
    }

    /**
     * Factory for the default content pane, called from
     * {@link #getContentPane()}. The JDK's returns a {@code JPanel} with a
     * {@code BorderLayout}; a bare {@code Div} is the equivalent here, since the
     * layout managers write their CSS onto whatever element they are given.
     */
    protected Component createContentPane() {
        return new Div();
    }

    // --- layered pane accessors --------------------------------------

    /** The layered pane, lazy-created and planted on first read. */
    public Component getLayeredPane() {
        if (layeredPane == null) setLayeredPane(createLayeredPane());
        return layeredPane;
    }

    /**
     * Null throws {@link IllegalComponentStateException} per JDK.
     *
     * <p>Faithfully <em>does not</em> migrate the menu bar or content pane into
     * the new pane — the JDK's {@code setLayeredPane} only swaps its own child,
     * leaving them parented to the outgoing pane, which is why nothing but a
     * look-and-feel ever calls it.
     */
    public void setLayeredPane(Component layered) {
        if (layered == null) {
            throw new IllegalComponentStateException("layeredPane cannot be set to null");
        }
        if (layered == this.layeredPane) return;
        Component old = this.layeredPane;
        if (old != null) old.getElement().removeFromParent();
        plant(getElement(), layered.getElement(), getElement().getChildCount());
        SHelper.markLayeredPane(layered);
        this.layeredPane = layered;
    }

    /** Factory for the default layered pane, called from {@link #getLayeredPane()}. */
    protected Component createLayeredPane() {
        return new Div();
    }

    // --- glass pane accessors ----------------------------------------

    /**
     * The glass pane — structural, and invisible by default as in the JDK, so
     * a later {@code setVisible(true)} actually curtains the window body and
     * swallows mouse input beneath it (D_glasspane_structural/SD_glasspane_structural).
     */
    public Component getGlassPane() {
        if (glassPane == null) setGlassPane(createGlassPane());
        return glassPane;
    }

    /**
     * Plant {@code glass} as this pane's first child — the JDK's index as well
     * as its containment ({@code rootPane.add(glassPane, 0)}). Null throws
     * {@link IllegalComponentStateException} per JDK.
     *
     * <p>Visibility is the pane's own concern: an invisible glass pane is
     * {@code display: none}, so the default neither paints nor blocks. The
     * incoming pane's visibility is left as the caller set it, matching the JDK's
     * documented "either order works" contract for swap-then-show.
     */
    public void setGlassPane(Component glass) {
        if (glass == null) {
            throw new IllegalComponentStateException("glassPane cannot be set to null");
        }
        if (glass == this.glassPane) return;
        Component old = this.glassPane;
        if (old != null) {
            old.getElement().removeFromParent();
            old.getElement().getClassList().remove("emul-glasspane");
        }
        plant(getElement(), glass.getElement(), 0);
        glass.getElement().getClassList().add("emul-glasspane");
        this.glassPane = glass;
    }

    /**
     * Factory for the default glass pane, called from {@link #getGlassPane()}.
     * The JDK's is an invisible transparent {@code JPanel}; ours is an invisible
     * {@code Div}.
     */
    protected Component createGlassPane() {
        Div gp = new Div();
        gp.setVisible(false);
        return gp;
    }

    // --- menu bar slot (SD_sjmenubar / D_menu_tree) ----------------------------------

    /** {@code null} until {@link #setJMenuBar} plants one. */
    public SJMenuBar getJMenuBar() {
        return menuBar;
    }

    /**
     * Plant {@code bar} at index 0 of the layered pane, above the content pane —
     * the JDK's containment ({@code layeredPane.add(bar, FRAME_CONTENT_LAYER)}),
     * with the CSS column supplying what {@code RootLayout} supplies there. Pass
     * {@code null} to detach.
     *
     * <p>Index 0 is load-bearing here in a way it is not in Swing: {@code RootLayout}
     * reads the {@code menuBar} field and ignores add order, while a flex column
     * reads DOM order.
     */
    public void setJMenuBar(SJMenuBar bar) {
        if (bar == this.menuBar) return;
        SJMenuBar old = this.menuBar;
        if (old != null) old.getElement().removeFromParent();
        if (bar != null) getLayeredPane().getElement().insertChild(0, bar.getElement());
        SHelper.markMenuBarSlot(bar, old);
        this.menuBar = bar;
    }

    // --- default-button wiring ---------------------------------------

    /** Currently-installed default button, or null when cleared. */
    public Button getDefaultButton() {
        return defaultButton;
    }

    /**
     * Install {@code button} as the root pane's default button — Enter
     * anywhere inside the enclosing window triggers its click, unless the
     * focused component claims it ({@link EnterClaims}).
     * Replacing or clearing tears down the previous Enter shortcut
     * first so the swap window doesn't double-trigger.
     *
     * <p>Scoped via {@code button} as the listener owner — detach or GC
     * of the button removes the shortcut automatically (UI-scoped
     * shortcut with button-tied lifecycle, matching JDK's
     * WHEN_IN_FOCUSED_WINDOW shape).
     *
     * <p>Click body funnels through {@link SHelper#callSwing} per R_callswing_envelope.
     * Fires {@code "defaultButton"} PCE on the surrogate's listener
     * list; the emulator wrapper fires its own PCE on its own layer
     * (two-layer convention, same as SJSlider / SJSpinner / SFrame).
     */
    public void setDefaultButton(Button button) {
        Button old = this.defaultButton;
        if (old == button) return;
        if (defaultButtonShortcut != null) {
            defaultButtonShortcut.remove();
            defaultButtonShortcut = null;
        }
        this.defaultButton = button;
        if (button != null) {
            defaultButtonShortcut = Shortcuts.addShortcutListener(
                    button,
                    () -> {
                        if (!EnterClaims.claimed(FocusTracker.getFocusOwner(), this, layerEnterClaims)) {
                            SHelper.callSwing(button::click);
                        }
                    },
                    Key.ENTER);
        }
        // Through the inherited mixin method, not PceSupport directly. Same
        // listeners either way, but the direct call was the module's only fire that
        // the audit scanner — which greps for firePropertyChange — could not see, so
        // this correct fire was reported as missing (SD_reverse_fanout_rows).
        firePropertyChange("defaultButton", old, button);
    }

    /**
     * Adds Enter claims the surrogates cannot know to the ones they declare as
     * {@link EnterClaims.Claimant}s:
     *
     * <pre>{@code
     * rootPane.setLayerEnterClaims(peer -> ComponentUtil.getData(peer, vaadinx.awt.Component.class)
     *         instanceof JTextField tf && tf.getActionListeners().length > 0);
     * }</pre>
     *
     * @param claims asked of the focused component and each ancestor below this
     *     pane, including ones that are no layer's peer
     */
    public void setLayerEnterClaims(SerializablePredicate<Component> claims) {
        this.layerEnterClaims = claims;
    }

    // --- L&F-parity read-only accessors ------------------------------

    /** JDK parity — some user code reads this reflectively for UIManager registration. */
    public String getUIClassID() {
        return "RootPaneUI";
    }

    // --- decoration-style constants (JDK parity) ---------------------
    //
    // Verbatim values from javax.swing.JRootPane. Migrated code that
    // passes these to setWindowDecorationStyle (which we don't honor —
    // Dialog always draws Vaadin's own chrome) still compiles.

    public static final int NONE = 0;
    public static final int FRAME = 1;
    public static final int PLAIN_DIALOG = 2;
    public static final int INFORMATION_DIALOG = 3;
    public static final int ERROR_DIALOG = 4;
    public static final int COLOR_CHOOSER_DIALOG = 5;
    public static final int FILE_CHOOSER_DIALOG = 6;
    public static final int QUESTION_DIALOG = 7;
    public static final int WARNING_DIALOG = 8;
}
