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

package vaadinx.awt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;

import java.awt.Color;
import java.awt.IllegalComponentStateException;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code W_frame_state_delegation} — the Frame/Dialog half of the JDK call-graph
 * audit; {@code D_frame_state_pairs} in {@code emulators/decisions.md}.
 *
 * <p>Two properties, and the second is the point of the front. The state values
 * are the same as before (a browser tab neither iconifies nor maximizes, so
 * every state stays NORMAL); what is new is that the four state methods now
 * form the JDK's graph instead of four independent leaves, so a subclass
 * override of {@code getExtendedState} / {@code setExtendedState} is actually reached —
 * R_no_vaadin_in_api limb 2.
 */
class FrameStateTest extends AbstractKaribuTest {

    // --- the state values, unchanged ---------------------------------

    @Test
    @DisplayName("state defaults to NORMAL on both accessors")
    void stateDefaultsToNormalOnBothAccessors() {
        Frame f = new Frame();
        assertEquals(java.awt.Frame.NORMAL, f.getState());
        assertEquals(java.awt.Frame.NORMAL, f.getExtendedState());
    }

    @Test
    @DisplayName("setState NORMAL is silent and leaves the frame NORMAL")
    void setStateNormalIsSilentAndLeavesTheFrameNormal() {
        Frame f = new Frame();
        f.setState(java.awt.Frame.NORMAL);
        assertEquals(java.awt.Frame.NORMAL, f.getState());
    }

    @Test
    @DisplayName("setState ICONIFIED is declined by the toolkit and stays NORMAL")
    void setStateIconifiedIsDeclinedByTheToolkitAndStaysNormal() {
        // The decline comes from Toolkit.isFrameStateSupported, not from a
        // constant in Frame — see the `state` field's javadoc.
        Frame f = new Frame();
        f.setState(java.awt.Frame.ICONIFIED);
        assertEquals(java.awt.Frame.NORMAL, f.getState());
        assertEquals(java.awt.Frame.NORMAL, f.getExtendedState());
    }

    @Test
    @DisplayName("setExtendedState MAXIMIZED_BOTH is declined and stays NORMAL")
    void setExtendedStateMaximizedBothIsDeclinedAndStaysNormal() {
        Frame f = new Frame();
        f.setExtendedState(java.awt.Frame.MAXIMIZED_BOTH);
        assertEquals(java.awt.Frame.NORMAL, f.getExtendedState());
        assertEquals(java.awt.Frame.NORMAL, f.getState());
    }

    @Test
    @DisplayName("setState ignores anything that is neither ICONIFIED nor NORMAL")
    void setStateIgnoresAnythingThatIsNeitherIconifiedNorNormal() {
        // Deliberate change of behaviour, adopted from the JDK: setState's
        // masked read-modify-write acts on the ICONIFIED bit alone, so a
        // MAXIMIZED request through the legacy setter is a silent no-op
        // rather than the WARN SB-Emulators used to emit. setExtendedState is the
        // setter that reports; this one does not reach it.
        Frame f = new Frame();
        f.setState(java.awt.Frame.MAXIMIZED_BOTH);
        assertEquals(java.awt.Frame.NORMAL, f.getExtendedState());
    }

    // --- the call graph: R_no_vaadin_in_api limb 2 -----------------------------------

    @Test
    @DisplayName("setState routes through setExtendedState so one override catches both")
    void setStateRoutesThroughSetExtendedStateSoOneOverrideCatchesBoth() {
        List<Integer> seen = new ArrayList<>();
        Frame f = new Frame() {
            @Override
            public void setExtendedState(int state) {
                seen.add(state);
                super.setExtendedState(state);
            }
        };
        f.setState(java.awt.Frame.ICONIFIED);
        assertEquals(List.of(java.awt.Frame.ICONIFIED), seen);
    }

    @Test
    @DisplayName("getState reads through getExtendedState")
    void getStateReadsThroughGetExtendedState() {
        Frame f = new Frame() {
            @Override
            public int getExtendedState() {
                return java.awt.Frame.ICONIFIED;
            }
        };
        // Nothing wrote the field — getState derives from the override, which
        // is exactly what the JDK does and what four independent leaves could
        // never do.
        assertEquals(java.awt.Frame.ICONIFIED, f.getState());
    }

    @Test
    @DisplayName("paramString reads the state through getExtendedState")
    void paramStringReadsTheStateThroughGetExtendedState() {
        Frame normal = new Frame();
        assertTrue(normal.toString().contains(",normal"), normal.toString());

        Frame iconified = new Frame() {
            @Override
            public int getExtendedState() {
                return java.awt.Frame.ICONIFIED;
            }
        };
        assertTrue(iconified.toString().contains(",iconified"), iconified.toString());
    }

    // --- the undecorated guards (Frame) -------------------------------

    @Test
    @DisplayName("translucency setters throw on a decorated frame")
    void translucencySettersThrowOnADecoratedFrame() {
        Frame f = new Frame();
        assertEquals("The frame is decorated",
                assertThrows(IllegalComponentStateException.class, () -> f.setOpacity(0.5f)).getMessage());
        assertEquals("The frame is decorated",
                assertThrows(IllegalComponentStateException.class,
                        () -> f.setShape(new Ellipse2D.Float(0f, 0f, 10f, 10f))).getMessage());
        assertEquals("The frame is decorated",
                assertThrows(IllegalComponentStateException.class,
                        () -> f.setBackground(new Color(255, 0, 0, 128))).getMessage());
    }

    @Test
    @DisplayName("translucency setters pass through on an undecorated frame")
    void translucencySettersPassThroughOnAnUndecoratedFrame() {
        Frame f = new Frame();
        f.setUndecorated(true);
        f.setOpacity(0.5f);
        f.setShape(new Ellipse2D.Float(0f, 0f, 10f, 10f));
        f.setBackground(new Color(255, 0, 0, 128));
    }

    @Test
    @DisplayName("opaque values never trip the guard on a decorated frame")
    void opaqueValuesNeverTripTheGuardOnADecoratedFrame() {
        // The guard is about translucency, not about the setters: a fully
        // opaque colour, a null shape and opacity 1.0 all reach Window's
        // inherited behaviour on a decorated frame.
        Frame f = new Frame();
        f.setOpacity(1.0f);
        f.setShape(null);
        f.setBackground(Color.RED);
        assertEquals(Color.RED, f.getBackground());
    }

    // --- the undecorated guards (Dialog) ------------------------------

    @Test
    @DisplayName("translucency setters throw on a decorated dialog")
    void translucencySettersThrowOnADecoratedDialog() {
        Dialog d = new Dialog(new Frame());
        assertEquals("The dialog is decorated",
                assertThrows(IllegalComponentStateException.class, () -> d.setOpacity(0.5f)).getMessage());
        assertEquals("The dialog is decorated",
                assertThrows(IllegalComponentStateException.class,
                        () -> d.setShape(new Ellipse2D.Float(0f, 0f, 10f, 10f))).getMessage());
        assertEquals("The dialog is decorated",
                assertThrows(IllegalComponentStateException.class,
                        () -> d.setBackground(new Color(255, 0, 0, 128))).getMessage());
    }

    @Test
    @DisplayName("translucency setters pass through on an undecorated dialog")
    void translucencySettersPassThroughOnAnUndecoratedDialog() {
        Dialog d = new Dialog(new Frame());
        d.setUndecorated(true);
        d.setOpacity(0.5f);
        d.setShape(new Ellipse2D.Float(0f, 0f, 10f, 10f));
        d.setBackground(new Color(255, 0, 0, 128));
    }
}
