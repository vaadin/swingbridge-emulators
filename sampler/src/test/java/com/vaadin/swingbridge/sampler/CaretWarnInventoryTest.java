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

package com.vaadin.swingbridge.sampler;

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.Routes;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.textfield.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vaadinx.EHelper;
import vaadinx.swing.JFrame;
import vaadinx.swing.JPanel;
import vaadinx.swing.JTextArea;
import vaadinx.swing.JTextField;

import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Caret view + caret/selection API surface exit gate (SD_caret_selection). Both tests fail if
 * any {@code EHelper.onUnimplemented} / {@code onUnsupported} /
 * {@code onUnsupportedPeerShape} — or the {@code SHelper} equivalents, since the
 * mechanism itself lives surrogate-side — fires during the asserted path:
 *
 * <ol>
 *   <li>{@link #inventory_caret_user_path} — {@link CaretPanel} driven
 *       end-to-end: panel swap, a browser-style edit through the
 *       auto-advance {@code PlainDocument} filters, and the caret readout
 *       its CaretListener drives.</li>
 *   <li>{@link #inventory_caret_api_surface} — a micro-driver over the whole
 *       caret + selection surface on an isolated fixture: dot/mark, the
 *       selection accessors, {@code replaceSelection}, the {@code Caret}
 *       view, and the listener families. Guards against any of it sliding
 *       back into the drop-and-WARN bucket SD_sjtextfield originally put it in.</li>
 * </ol>
 *
 * <p>Not covered here, and not coverable here: the {@code setSelectionRange}
 * push and the {@code emul-selection} bridge both run client-side, and Karibu
 * records {@code executeJs} without executing it. Those are browser-verified
 * instead — the results table is in SD_caret_selection.
 */
class CaretWarnInventoryTest {

    private static Routes routes;

    @BeforeAll
    static void discoverViews() {
        routes = new Routes().autoDiscoverViews("com.vaadin.swingbridge.sampler");
    }

    @BeforeEach
    void mockVaadin() {
        MockVirtualThreadAwareServlet.setupMockVaadin(routes);
    }

    @AfterEach
    void tearDown() {
        MockVaadin.tearDown();
        EHelper.warnHook = msg -> {};
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = msg -> {};
    }

    @Test
    void inventory_caret_user_path() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> collect = warnings::add;
        EHelper.warnHook = collect;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = collect;

        UI.getCurrent().navigate(SamplerRoute.class);
        LocatorJ._assertOne(SamplerRoute.class);
        dump("Step 0a (Sampler shell + HomePanel)", warnings);

        Navigate.to("Caret");
        dump("Step 0b (CaretPanel swap)", warnings);

        // The panel's three TextField peers, in construction order: the main
        // demo field, then the two auto-advance phone fields.
        List<TextField> fields = LocatorJ._find(TextField.class);
        dump("Step 1 (lookups)", warnings);

        // Step 2: the server-side movers behind the panel's buttons. Clicking
        // each button is what a user does; going through the buttons keeps the
        // ActionListener wiring in the asserted path too.
        clickButton("Select \"quick\"");
        dump("Step 2a (select)", warnings);
        clickButton("Caret to start");
        dump("Step 2b (setCaretPosition)", warnings);
        clickButton("Select all");
        dump("Step 2c (selectAll)", warnings);
        clickButton("Replace selection with \"slow\"");
        dump("Step 2d (replaceSelection over a selection)", warnings);

        // Step 3: browser-style typing into the auto-advance field. Its
        // PlainDocument filter strips non-digits, then calls requestFocus +
        // setCaretPosition on the next field once full — the inventory app's
        // NumberFormatDocument shape, which is why SD_caret_selection exists.
        LocatorJ._setValue(fields.get(1), "415");
        dump("Step 3 (auto-advance filter fires on a browser edit)", warnings);

        // Step 4: the text area's selection path, which reaches the same mixin
        // through a different Vaadin peer type.
        clickButton("Select line 2");
        dump("Step 4 (JTextArea selection)", warnings);

        WarnDump.println();
        WarnDump.println("=== caret user-path WARN total: " + warnings.size() + " ===");
    }

    @Test
    void inventory_caret_api_surface() {
        List<String> warnings = new ArrayList<>();
        Consumer<String> collect = warnings::add;
        EHelper.warnHook = collect;
        com.vaadin.swingbridge.surrogates.SHelper.warnHook = collect;

        JFrame frame = new JFrame("driver");
        JPanel panel = new JPanel();
        frame.add(panel);
        dump("Fixture setup", warnings);

        // -- Bucket a: dot / mark --

        JTextField field = new JTextField("The quick brown fox");
        panel.add(field);
        field.setCaretPosition(4);
        field.moveCaretPosition(9);
        field.getCaretPosition();
        dump("Bucket a (dot/mark)", warnings);

        // -- Bucket b: the selection accessors --

        field.getSelectionStart();
        field.getSelectionEnd();
        field.getSelectedText();
        field.setSelectionStart(2);
        field.setSelectionEnd(8);
        field.select(0, 3);
        field.selectAll();
        dump("Bucket b (selection accessors)", warnings);

        // -- Bucket c: replaceSelection, which edits through the Document --

        field.select(4, 9);
        field.replaceSelection("slow");
        field.replaceSelection(null);
        dump("Bucket c (replaceSelection)", warnings);

        // -- Bucket d: the Caret view. Dot, mark and the change listeners are
        //    real; isVisible / isSelectionVisible / getBlinkRate answer as a
        //    focused HTML input would, deliberately without WARNing (SD_caret_selection).

        javax.swing.text.Caret caret = field.getCaret();
        caret.setDot(3);
        caret.moveDot(7);
        caret.getDot();
        caret.getMark();
        caret.isVisible();
        caret.isSelectionVisible();
        caret.getBlinkRate();
        dump("Bucket d (Caret view)", warnings);

        // -- Bucket e: the listener families, both of which fan out over the
        //    selection bridge.

        CaretListener caretListener = (CaretEvent e) -> { };
        javax.swing.event.ChangeListener changeListener = e -> { };
        field.addCaretListener(caretListener);
        caret.addChangeListener(changeListener);
        field.setCaretPosition(1);
        field.getCaretListeners();
        field.removeCaretListener(caretListener);
        caret.removeChangeListener(changeListener);
        dump("Bucket e (caret listeners)", warnings);

        // -- Bucket f: the same surface on a JTextArea peer --

        JTextArea area = new JTextArea("line one\nline two");
        panel.add(area);
        area.select(0, 8);
        area.getSelectedText();
        area.setCaretPosition(area.getText().length());
        area.getCaret().getDot();
        dump("Bucket f (JTextArea)", warnings);

        WarnDump.println();
        WarnDump.println("=== caret API-surface WARN total: " + warnings.size() + " ===");
    }

    private static void clickButton(String text) {
        LocatorJ._click(LocatorJ._get(com.vaadin.flow.component.button.Button.class,
                spec -> spec.withText(text)));
    }

    private static void dump(String banner, List<String> warnings) {
        WarnDump.println();
        WarnDump.println("--- " + banner + " (" + warnings.size() + " stub call"
                + (warnings.size() == 1 ? "" : "s") + ") ---");
        for (String w : warnings) {
            WarnDump.println("  " + w);
        }
        if (!warnings.isEmpty()) {
            String msg = "[" + banner + "] " + warnings.size()
                    + " stub WARN(s) fired — regression in the caret exit gate: "
                    + warnings;
            warnings.clear();
            throw new AssertionError(msg);
        }
        warnings.clear();
    }
}
