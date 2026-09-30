/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation, with the following
 * "Classpath" exception:
 *
 *     Linking this library statically or dynamically with other modules
 *     is making a combined work based on this library.  Thus, the terms
 *     and conditions of the GNU General Public License cover the whole
 *     combination.
 *
 *     As a special exception, the copyright holders of this library give
 *     you permission to link this library with independent modules to
 *     produce an executable, regardless of the license terms of these
 *     independent modules, and to copy and distribute the resulting
 *     executable under terms of your choice, provided that you also meet,
 *     for each linked independent module, the terms and conditions of the
 *     license of that module.  An independent module is a module which is
 *     not derived from or based on this library.  If you modify this
 *     library, you may extend this exception to your version of the
 *     library, but you are not obligated to do so.  If you do not wish to
 *     do so, delete this exception statement from your version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 */

package vaadinx.swing;

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Input;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJColorChooser;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;

import javax.swing.colorchooser.DefaultColorSelectionModel;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour for the {@link JColorChooser} emulator (D_jcolorchooser). The delegation surface
 * mirrors JProgressBar/JSlider; the blocking {@code JColorChooser.showDialog} statics
 * are driven with the JFileChooser/JDialog VT-park harness — call the static
 * inside {@link EHelper#callSwing} so the internal modal JDialog parks, interact from
 * the test thread, unpark via an OK/Cancel click in another callSwing.
 */
class JColorChooserTest extends AbstractKaribuTest {

    /** Run {@code show} (which parks) on a VT, returning the color it produced once unparked. */
    private static Color runShow(Supplier<Color> show, Runnable interact) throws InterruptedException {
        AtomicReference<Color> result = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            result.set(show.get());
            finished.countDown();
        });
        interact.run();
        assertTrue(finished.await(5, TimeUnit.SECONDS), "showDialog did not return");
        return result.get();
    }

    private static void clickButton(String text) {
        Button btn = LocatorJ._get(Button.class, spec -> spec.withText(text));
        EHelper.callSwing(() -> LocatorJ._click(btn));
    }

    // --- R_leaf_peer_lockdown lock-down --------------------------------------------------

    @Test
    @DisplayName("R_leaf_peer_lockdown lock-down — no protected ctor on the leaf")
    void noProtectedCtorOnTheLeaf() {
        List<Constructor<?>> protectedCtors =
                Arrays.stream(JColorChooser.class.getDeclaredConstructors())
                        .filter(ctor -> Modifier.isProtected(ctor.getModifiers()))
                        .toList();
        assertTrue(protectedCtors.isEmpty(),
                "R_leaf_peer_lockdown: no protected ctor allowed on JDK-leaf JColorChooser; got: "
                        + protectedCtors);
    }

    @Test
    @DisplayName("peer is always an SJColorChooser")
    void peerIsAlwaysAnSJColorChooser() {
        assertInstanceOf(SJColorChooser.class, new JColorChooser().getPeer());
        assertInstanceOf(SJColorChooser.class, new JColorChooser(Color.RED).getPeer());
        assertInstanceOf(SJColorChooser.class,
                new JColorChooser(new DefaultColorSelectionModel(Color.GREEN)).getPeer());
    }

    // --- Delegation -----------------------------------------------------

    @Test
    @DisplayName("ctor variants seed the color")
    void ctorVariantsSeedTheColor() {
        assertEquals(Color.white, new JColorChooser().getColor());
        assertEquals(Color.RED, new JColorChooser(Color.RED).getColor());
        assertEquals(Color.GREEN,
                new JColorChooser(new DefaultColorSelectionModel(Color.GREEN)).getColor());
    }

    @Test
    @DisplayName("setColor reaches the rendered input; the peer's model is its own")
    void setColorReachesTheRenderedInput() {
        JColorChooser cc = new JColorChooser();
        SJColorChooser peer = (SJColorChooser) cc.getPeer();
        cc.setColor(new Color(0x12, 0x34, 0x56));
        assertEquals(new Color(0x12, 0x34, 0x56), cc.getColor());
        assertEquals("#123456", ((Input) peer).getValue());
        assertNotSame(cc.getSelectionModel(), peer.getSelectionModel());
    }

    @Test
    @DisplayName("setColor int overloads")
    void setColorIntOverloads() {
        JColorChooser cc = new JColorChooser();
        cc.setColor(0x10, 0x20, 0x30);
        assertEquals(new Color(0x10, 0x20, 0x30), cc.getColor());
        cc.setColor(0x405060);
        assertEquals(new Color(0x40, 0x50, 0x60), cc.getColor());
    }

    // --- Browser picks ------------------------------------------------

    /** Attaches {@code cc} and returns its native color input. */
    private static Input attachedInput(JColorChooser cc) {
        com.vaadin.flow.component.UI.getCurrent().add(cc.getPeer());
        return (Input) cc.getPeer();
    }

    @Test
    @DisplayName("a browser pick writes the model inside a UI fiber, as a desktop chooser panel does")
    void browserPickWritesTheModelInAUiFiber() {
        JColorChooser cc = new JColorChooser(Color.RED);
        Input input = attachedInput(cc);
        List<String> seen = new ArrayList<>();
        cc.getSelectionModel().addChangeListener(e -> seen.add(cc.getColor() + " fiber="
                + com.github.mvysny.blockingdialogs.UIFibers.isInUIFiber()
                + " source=" + e.getSource().getClass().getSimpleName()));

        LocatorJ._setValue(input, "#00ff00");

        assertEquals(List.of(new Color(0, 255, 0) + " fiber=true source=DefaultColorSelectionModel"), seen);
    }

    @Test
    @DisplayName("a pick reaches a model the migrator installed, through getSelectionModel")
    void browserPickReachesAnInstalledModel() {
        JColorChooser cc = new JColorChooser(Color.RED);
        Input input = attachedInput(cc);
        DefaultColorSelectionModel mine = new DefaultColorSelectionModel(Color.BLACK);
        cc.setSelectionModel(mine);
        assertEquals("#000000", input.getValue(), "the swap flushed the new model's color");

        LocatorJ._setValue(input, "#0000ff");

        assertEquals(Color.BLUE, mine.getSelectedColor());
    }

    @Test
    @DisplayName("a model listener opened by a pick can park on a modal dialog")
    void pickListenerCanOpenAModalDialog() throws InterruptedException {
        JColorChooser cc = new JColorChooser(Color.RED);
        Input input = attachedInput(cc);
        CountDownLatch finished = new CountDownLatch(1);
        cc.getSelectionModel().addChangeListener(e -> {
            JOptionPane.showMessageDialog(null, "Picked");
            finished.countDown();
        });

        LocatorJ._setValue(input, "#00ff00");
        clickButton("OK");

        assertTrue(finished.await(5, TimeUnit.SECONDS), "the listener did not resume");
    }

    // --- The JDK's bodies ----------------------------------------------

    /**
     * A script measured on JDK 25 (headless): constructor nulls, the five default chooser
     * panels, the panel mutators and their exceptions, the model swap and the events.
     */
    @Test
    @DisplayName("the chooser state replays the JDK's script")
    void stateMatchesTheJdk() {
        assertNull(new JColorChooser((Color) null).getColor());
        assertThrows(NullPointerException.class, () -> new JColorChooser((javax.swing.colorchooser.ColorSelectionModel) null));

        JColorChooser c = new JColorChooser();
        assertEquals(Color.white, c.getColor());
        assertEquals(List.of("Swatches", "HSV", "HSL", "RGB", "CMYK"), displayNames(c));
        assertNotSame(c.getChooserPanels(), c.getChooserPanels());
        assertNull(c.getPreviewPanel());
        assertFalse(c.getDragEnabled());

        List<String> log = new ArrayList<>();
        c.addPropertyChangeListener(e -> log.add(e.getPropertyName()));
        c.getSelectionModel().addChangeListener(e -> log.add("model change"));
        c.setColor(Color.red);
        c.setColor(Color.red);
        c.setColor(0, 255, 0);
        c.setColor(0x0000ff);
        c.setColor(0xff123456);
        assertEquals(255, c.getColor().getAlpha(), "setColor(int) drops alpha");
        c.setColor((Color) null);
        assertEquals(new Color(0x12, 0x34, 0x56), c.getColor(), "a null color is ignored");
        assertEquals(List.of("model change", "model change", "model change", "model change"), log);
        log.clear();

        javax.swing.colorchooser.ColorSelectionModel m1 = c.getSelectionModel();
        DefaultColorSelectionModel m2 = new DefaultColorSelectionModel(Color.yellow);
        c.setSelectionModel(m2);
        c.setSelectionModel(m2);
        assertEquals(Color.yellow, c.getColor());
        assertEquals(List.of("selectionModel"), log);
        log.clear();
        m1.setSelectedColor(Color.cyan);
        assertEquals(List.of("model change"), log, "the old model's own listener still hears it");
        assertEquals(Color.yellow, c.getColor());
        log.clear();
        assertThrows(NullPointerException.class, () -> c.setSelectionModel(null));
        assertNull(c.getSelectionModel());
        assertEquals(List.of(), log, "the NPE comes before the event");

        JColorChooser d = new JColorChooser(Color.blue);
        List<String> panelEvents = new ArrayList<>();
        d.addPropertyChangeListener(e -> panelEvents.add(e.getPropertyName() + " " + size(e.getOldValue())
                + "->" + size(e.getNewValue())));
        javax.swing.colorchooser.AbstractColorChooserPanel[] ps = d.getChooserPanels();
        assertSame(ps[0], d.removeChooserPanel(ps[0]));
        assertEquals(List.of("HSV", "HSL", "RGB", "CMYK"), displayNames(d));
        IllegalArgumentException absent = assertThrows(IllegalArgumentException.class, () -> d.removeChooserPanel(ps[0]));
        assertEquals("chooser panel not in this chooser", absent.getMessage());
        d.addChooserPanel(ps[0]);
        assertEquals(List.of("HSV", "HSL", "RGB", "CMYK", "Swatches"), displayNames(d));
        javax.swing.colorchooser.AbstractColorChooserPanel[] mine = {ps[1]};
        d.setChooserPanels(mine);
        mine[0] = ps[2];
        assertEquals(List.of("HSV"), displayNames(d), "a defensive copy");
        assertThrows(NullPointerException.class, () -> d.setChooserPanels(null));
        assertThrows(IllegalArgumentException.class, () -> d.removeChooserPanel(null));
        assertThrows(NullPointerException.class, () -> d.addChooserPanel(null));
        assertEquals(2, d.getChooserPanels().length, "the null was stored before the throw");
        JPanel preview = new JPanel();
        d.setPreviewPanel(preview);
        d.setPreviewPanel(preview);
        d.setPreviewPanel(null);
        assertEquals(List.of("chooserPanels 5->4", "chooserPanels 4->5", "chooserPanels 5->1",
                "previewPanel -->JPanel", "previewPanel JPanel->-"), panelEvents);
        assertTrue(d.toString().endsWith(",previewPanel=]"), d.toString());
    }

    private static List<String> displayNames(JColorChooser c) {
        List<String> names = new ArrayList<>();
        for (javax.swing.colorchooser.AbstractColorChooserPanel p : c.getChooserPanels()) {
            names.add(p.getDisplayName());
        }
        return names;
    }

    /** An array's length, a component's simple class name, or "-" for null. */
    private static String size(Object v) {
        if (v == null) return "-";
        if (v instanceof Object[] a) return String.valueOf(a.length);
        return v.getClass().getSimpleName();
    }

    // --- PCE on model swap ---------------------------------------------

    @Test
    @DisplayName("setSelectionModel fires PCE on the emulator")
    void setSelectionModelFiresPce() {
        JColorChooser cc = new JColorChooser(Color.RED);
        List<PropertyChangeEvent> pces = new ArrayList<>();
        cc.addPropertyChangeListener(JColorChooser.SELECTION_MODEL_PROPERTY, pces::add);
        DefaultColorSelectionModel fresh = new DefaultColorSelectionModel(Color.GREEN);
        cc.setSelectionModel(fresh);
        assertEquals(1, pces.size());
        assertSame(fresh, cc.getSelectionModel());
        assertEquals(Color.GREEN, cc.getColor());
    }

    // --- Blocking showDialog (VT park) ---------------------------------

    @Test
    @DisplayName("showDialog from non-VT context throws ISE")
    void showDialogOutsideAVtThrows() {
        assertThrows(IllegalStateException.class,
                () -> JColorChooser.showDialog(null, "Pick", Color.RED));
    }

    @Test
    @DisplayName("showDialog OK returns the chosen color")
    void showDialogOkReturnsTheChosenColor() throws InterruptedException {
        // No browser pick — OK returns the initial color the pane was seeded with.
        Color chosen = runShow(
                () -> JColorChooser.showDialog(null, "Pick", new Color(0x11, 0x22, 0x33)),
                () -> clickButton("OK"));
        assertEquals(new Color(0x11, 0x22, 0x33), chosen);
    }

    @Test
    @DisplayName("showDialog reflects a browser pick before OK")
    void showDialogReflectsABrowserPick() throws InterruptedException {
        Color chosen = runShow(
                () -> JColorChooser.showDialog(null, "Pick", Color.BLACK),
                () -> {
                    // Simulate the user picking a color in the embedded native input.
                    SJColorChooser peer = LocatorJ._get(SJColorChooser.class);
                    LocatorJ._setValue((Input) peer, "#00ff00");
                    clickButton("OK");
                });
        assertEquals(Color.GREEN, chosen);
    }

    @Test
    @DisplayName("showDialog Cancel returns null")
    void showDialogCancelReturnsNull() throws InterruptedException {
        Color chosen = runShow(
                () -> JColorChooser.showDialog(null, "Pick", Color.RED),
                () -> clickButton("Cancel"));
        assertNull(chosen);
    }

    // --- createDialog invokes the caller's listeners -------------------

    @Test
    @DisplayName("createDialog OK invokes the ok listener")
    void createDialogOkInvokesTheOkListener() throws InterruptedException {
        JColorChooser pane = new JColorChooser(Color.RED);
        AtomicReference<ActionEvent> okFired = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);
        EHelper.callSwing(() -> {
            JDialog dialog = JColorChooser.createDialog(null, "Pick", true, pane,
                    okFired::set,
                    (ActionListener) e -> {
                    });
            dialog.setVisible(true); // parks
            finished.countDown();
        });
        clickButton("OK");
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertSame(pane, okFired.get().getSource());
    }

    // --- L&F -----------------------------------------------------------

    @Test
    @DisplayName("getUIClassID is ColorChooserUI")
    void uiClassIdIsColorChooserUI() {
        assertEquals("ColorChooserUI", new JColorChooser().getUIClassID());
    }
}
