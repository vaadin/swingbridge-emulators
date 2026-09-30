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

import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AWT's revalidate() walk: invalidate self, find the nearest validate-root
 * ancestor (or the top of the tree), validate that. The legs are mostly
 * no-ops in our world (Vaadin re-lays out on DOM change, R_layouts_close_enough), but the
 * dispatch still has to reach the right container so user-overridden
 * invalidate/validate/isValidateRoot see the correct calls (R_swing_is_truth).
 */
class ComponentRevalidateTest extends AbstractKaribuTest {

    private static final class RecordingContainer extends Container {
        final List<String> validateCalls = new ArrayList<>();
        final List<String> invalidateCalls = new ArrayList<>();
        private final String tag;
        private final boolean validateRoot;

        RecordingContainer(String tag, boolean validateRoot) {
            this.tag = tag;
            this.validateRoot = validateRoot;
        }

        @Override
        public boolean isValidateRoot() {
            return validateRoot;
        }

        @Override
        public void validate() {
            validateCalls.add(tag);
            super.validate();
        }

        @Override
        public void invalidate() {
            invalidateCalls.add(tag);
            super.invalidate();
        }
    }

    private static final class RecordingLeaf extends Component {
        final List<String> validateCalls = new ArrayList<>();
        final List<String> invalidateCalls = new ArrayList<>();

        RecordingLeaf() {
            super(new Div());
        }

        @Override
        public void validate() {
            validateCalls.add("leaf");
            super.validate();
        }

        @Override
        public void invalidate() {
            invalidateCalls.add("leaf");
            super.invalidate();
        }
    }

    @Test
    @DisplayName("revalidate invalidates self")
    void revalidateInvalidatesSelf() {
        RecordingLeaf leaf = new RecordingLeaf();
        leaf.revalidate();
        assertEquals(List.of("leaf"), leaf.invalidateCalls);
    }

    @Test
    @DisplayName("revalidate on orphan validates self")
    void revalidateOnOrphanValidatesSelf() {
        // No parent → AWT validates this component directly rather than
        // walking up. Without this branch, orphan revalidate would be a no-op.
        RecordingLeaf leaf = new RecordingLeaf();
        leaf.revalidate();
        assertEquals(List.of("leaf"), leaf.validateCalls);
    }

    @Test
    @DisplayName("revalidate on child of validate-root validates the root")
    void revalidateOnChildOfValidateRootValidatesTheRoot() {
        RecordingContainer root = new RecordingContainer("root", true);
        RecordingLeaf leaf = new RecordingLeaf();
        root.add(leaf);

        leaf.revalidate();

        assertEquals(List.of("root"), root.validateCalls);
        // leaf itself was invalidated but not validated — the validate call
        // went to the validate-root ancestor, not to self.
        assertEquals(List.of("leaf"), leaf.invalidateCalls);
        assertTrue(leaf.validateCalls.isEmpty());
    }

    @Test
    @DisplayName("revalidate walks past non-validate-root containers to find a validate-root")
    void revalidateWalksPastNonValidateRootContainersToFindAValidateRoot() {
        RecordingContainer outer = new RecordingContainer("outer", true);
        RecordingContainer middle = new RecordingContainer("middle", false);
        RecordingContainer inner = new RecordingContainer("inner", false);
        RecordingLeaf leaf = new RecordingLeaf();
        outer.add(middle);
        middle.add(inner);
        inner.add(leaf);

        leaf.revalidate();

        // Walk: leaf.parent = inner (not root) → middle (not root) → outer (root).
        // Only outer.validate() fires; intermediates don't.
        assertEquals(List.of("outer"), outer.validateCalls);
        assertTrue(middle.validateCalls.isEmpty());
        assertTrue(inner.validateCalls.isEmpty());
    }

    @Test
    @DisplayName("revalidate validates the top when no validate-root is found")
    void revalidateValidatesTheTopWhenNoValidateRootIsFound() {
        // Nothing in the chain claims to be a validate-root. AWT walks to the
        // tree root and validates whatever's there, rather than giving up.
        RecordingContainer top = new RecordingContainer("top", false);
        RecordingContainer middle = new RecordingContainer("middle", false);
        RecordingLeaf leaf = new RecordingLeaf();
        top.add(middle);
        middle.add(leaf);

        leaf.revalidate();

        assertEquals(List.of("top"), top.validateCalls);
        assertTrue(middle.validateCalls.isEmpty());
    }

    @Test
    @DisplayName("Window is a validate-root so revalidate stops at it")
    void windowIsAValidateRootSoRevalidateStopsAtIt() {
        // R_swing_is_truth: AWT's Window.isValidateRoot is true — the whole reason Window
        // exists in this walk is to catch revalidate before it climbs off
        // the tree. Smoke-check the flag; the walk tests above rely on it.
        Window w = new Window(new Frame());
        assertTrue(w.isValidateRoot());
    }
}
