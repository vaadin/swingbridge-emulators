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

import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.github.mvysny.kaributesting.v10.ShortcutsKt;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.SpinnerDateModel;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The default button's Enter against the focused component's own Enter binding — Swing's
 * {@code WHEN_FOCUSED} before {@code WHEN_IN_FOCUSED_WINDOW}, as each JDK action's
 * {@code isEnabled()} decides it (SD_enter_claims).
 */
class EnterClaimsTest extends AbstractKaribuTest {

    private SJRootPane rootPane;
    private final AtomicInteger clicks = new AtomicInteger();

    @BeforeEach
    void installDefaultButton() {
        rootPane = new SJRootPane();
        Button ok = new Button("OK", e -> clicks.incrementAndGet());
        ((Div) rootPane.getContentPane()).add(ok);
        rootPane.setDefaultButton(ok);
        UI.getCurrent().add(rootPane);
    }

    /** Focuses {@code field} inside the root pane and presses Enter. */
    private int enterIn(Component field) {
        FocusTracker.setFocusOwner(field);
        clicks.set(0);
        ShortcutsKt.fireShortcut(Key.ENTER);
        return clicks.get();
    }

    private <C extends Component> C add(C field) {
        ((Div) rootPane.getContentPane()).add(field);
        return field;
    }

    @Test
    @DisplayName("an unclaimed Enter clicks the default button — a plain text field, or nothing focused")
    void unclaimedEnterClicks() {
        assertEquals(1, enterIn(add(new SJTextField())));
        assertEquals(1, enterIn(null));
    }

    @Test
    @DisplayName("an editable text area keeps Enter for its newline; a read-only one lets it through")
    void textAreaClaimsWhileEditable() {
        SJTextArea area = add(new SJTextArea());
        assertEquals(0, enterIn(area));
        area.setEditable(false);
        assertEquals(1, enterIn(area));
    }

    @Test
    @DisplayName("an editable text area stops its Enter in the browser, so the shortcut cannot cancel the newline")
    void textAreaStopsEnterPropagationWhileEditable() {
        // Browser-only behaviour — Karibu never dispatches DOM events — so this pins
        // the registration: the shortcut preventDefault()s every Enter it sees, and
        // measured in the browser, the area lost its newline until this stop.
        SJTextArea area = new SJTextArea();
        var expressions = area.getElement().getNode()
                .getFeature(com.vaadin.flow.internal.nodefeature.ElementListenerMap.class)
                .getExpressions("keydown");
        assertTrue(expressions.stream().anyMatch(it -> it.contains("event.key === 'Enter' && !element.readonly")),
                expressions.toString());
        assertTrue(expressions.stream().anyMatch(it -> it.contains("stopPropagation")), expressions.toString());
    }

    @Test
    @DisplayName("an editable editor pane keeps Enter; a read-only one lets it through")
    void editorPaneClaimsWhileEditable() {
        SJEditorPane pane = add(new SJEditorPane());
        assertEquals(0, enterIn(pane));
        pane.setEditable(false);
        assertEquals(1, enterIn(pane));
    }

    @Test
    @DisplayName("a text field with an ActionListener keeps Enter for its ActionEvent")
    void textFieldClaimsWithAnActionListener() {
        SJTextField field = add(new SJTextField());
        field.addActionListener(e -> { });
        assertEquals(0, enterIn(field));

        SJPasswordField password = add(new SJPasswordField());
        password.addActionListener(e -> { });
        assertEquals(0, enterIn(password));
    }

    // The commit rule is asserted on claimsEnter() rather than through the shortcut:
    // Karibu's fireShortcut runs a client round trip before it dispatches, which
    // ends the round trip the commit belongs to. In the browser both arrive in one
    // request, value first (measured); a /guide-migrateapp round drives that end to end.

    @Test
    @DisplayName("a browser commit claims Enter until the round trip ends, as an edit claims Swing's commit")
    void formattedFieldClaimsForTheCommittingRoundTrip() {
        SJFormattedIntegerField field = add(new SJFormattedIntegerField(1));
        LocatorJ._setValue(field, 7);
        assertTrue(field.claimsEnter(), "the browser committed 7 in this round trip");

        MockVaadin.clientRoundtrip();
        assertFalse(field.claimsEnter(), "a later round trip: nothing is edited any more");
        assertEquals(1, enterIn(field));
    }

    @Test
    @DisplayName("a programmatic value change is not an edit")
    void programmaticChangeIsNoEdit() {
        SJFormattedTextField field = add(new SJFormattedTextField());
        field.setValue("typed by code");
        assertFalse(field.claimsEnter());
        assertEquals(1, enterIn(field));
    }

    @Test
    @DisplayName("a formatted text field claims by edit, not by ActionListener")
    void formattedTextFieldIgnoresActionListeners() {
        SJFormattedTextField field = add(new SJFormattedTextField());
        field.addActionListener(e -> { });
        assertEquals(1, enterIn(field));
    }

    @Test
    @DisplayName("a date spinner's inner picker commits for the spinner, and focus on the inner field finds it")
    void spinnerClaimsThroughItsInnerField() {
        SJSpinner spinner = add(new SJSpinner(new SpinnerDateModel()));
        DatePicker inner = LocatorJ._get(spinner, DatePicker.class);
        LocatorJ._setValue(inner, LocalDate.of(1815, 12, 11));
        assertTrue(spinner.claimsEnter());
        assertTrue(EnterClaims.claimed(inner, rootPane, c -> false), "the walk reaches the spinner");

        MockVaadin.clientRoundtrip();
        assertEquals(1, enterIn(inner));
    }

    @Test
    @DisplayName("focus outside the root pane claims nothing, whatever holds it")
    void focusOutsideTheRootPaneClaimsNothing() {
        SJTextArea elsewhere = new SJTextArea();
        UI.getCurrent().add(elsewhere);
        assertEquals(1, enterIn(elsewhere));
    }

    @Test
    @DisplayName("a layer above adds claims the surrogates cannot see")
    void layerClaimsApply() {
        SJTextField field = add(new SJTextField());
        rootPane.setLayerEnterClaims(c -> c == field);
        assertEquals(0, enterIn(field));
    }
}
