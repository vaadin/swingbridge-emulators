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

import com.github.mvysny.kaributesting.v10.MockVaadin;
import com.vaadin.flow.component.UI;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.vaadin.swingbridge.surrogates.SJSlider;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D_field_write_reconcile: a migrated subclass's <b>direct write</b> to a JDK-shaped
 * protected field (bypassing the setter) is detected and pushed to the peer — at the
 * callSwing epilogue in steady state, at the one-shot beforeClientResponse during
 * bootstrap. JSlider is the reference class; the same machinery serves the other
 * D_field_write_reconcile emulators. This test lives in {@code vaadinx.swing} so it can
 * perform the protected-field writes a migrated subclass would.
 */
public class FieldReconcilerTest extends AbstractKaribuTest {

    @Test
    @DisplayName("direct value-field write is pushed to the peer by the callSwing epilogue")
    public void directValueWriteReconciledAtCallSwingEpilogue() {
        JSlider slider = new JSlider();
        SJSlider peer = (SJSlider) slider.getPeer();
        assertFalse(peer.getSnapToTicks());

        slider.snapToTicks = true;           // the antipattern: field write, no setter
        assertFalse(peer.getSnapToTicks());  // nothing pushed yet

        EHelper.callSwing(() -> { });        // any envelope — a click, a Timer fire, ...
        assertTrue(peer.getSnapToTicks());
        assertTrue(slider.getSnapToTicks());
    }

    @Test
    @DisplayName("direct model-field write is pushed to the peer by the callSwing epilogue")
    public void directModelWriteReconciledAtCallSwingEpilogue() {
        JSlider slider = new JSlider(0, 100, 50);
        SJSlider peer = (SJSlider) slider.getPeer();
        javax.swing.BoundedRangeModel replacement = new javax.swing.DefaultBoundedRangeModel(7, 0, 0, 10);

        slider.sliderModel = replacement;    // the antipattern
        assertNotSame(replacement, peer.getModel());

        EHelper.callSwing(() -> { });
        assertSame(replacement, peer.getModel());
        assertEquals(7, slider.getValue());
    }

    @Test
    @DisplayName("bootstrap-time write (before any callSwing) is caught by the one-shot flush hook")
    public void bootstrapWriteReconciledAtClientResponse() {
        JSlider slider = new JSlider();
        SJSlider peer = (SJSlider) slider.getPeer();
        UI.getCurrent().add(peer);           // attach, as bootstrap's UI build does

        slider.majorTickSpacing = 25;        // ctor-time app code writing the field directly
        assertEquals(0, peer.getMajorTickSpacing());

        MockVaadin.clientRoundtrip();        // the response flush that follows bootstrap
        assertEquals(25, peer.getMajorTickSpacing());
    }

    @Test
    @DisplayName("setter writes reconcile nothing — no false positives")
    public void setterWritesAreNotReported() {
        JSlider slider = new JSlider();
        SJSlider peer = (SJSlider) slider.getPeer();
        slider.setSnapToTicks(true);
        slider.setMajorTickSpacing(10);
        slider.setModel(new javax.swing.DefaultBoundedRangeModel(1, 0, 0, 5));

        // The epilogue runs after every callSwing; a legitimate setter must leave nothing
        // to repair (a false positive here would ERROR-spam every roundtrip).
        EHelper.callSwing(() -> { });
        assertTrue(peer.getSnapToTicks());
        assertEquals(10, peer.getMajorTickSpacing());
        assertEquals(1, slider.getValue());
    }
}
