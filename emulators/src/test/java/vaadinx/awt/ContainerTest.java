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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EHelper;
import vaadinx.awt.event.ContainerEvent;
import vaadinx.awt.event.ContainerListener;
import vaadinx.awt.event.HierarchyEvent;
import vaadinx.awt.event.HierarchyListener;

import java.awt.AWTKeyStroke;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.IllegalComponentStateException;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
// Single-type-import: shadows the vaadinx.awt.List emulator in this package.
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static vaadinx.TestAssertions.assertSingle;

class ContainerTest extends AbstractKaribuTest {

    private Component component() {
        return new Component(new Div()) {
        };
    }

    /** The container's children as a List, so the assertions can compare whole sequences. */
    private static List<Component> childrenOf(Container c) {
        return Arrays.asList(c.getComponents());
    }

    @Test
    @DisplayName("can instantiate")
    void canInstantiate() {
        new Container();
    }

    @Test
    @DisplayName("add sets parent and appends to peer DOM")
    void addSetsParentAndAppendsToPeerDom() {
        Container parent = new Container();
        Component child = component();

        parent.add(child);

        assertSame(parent, child.getParent());
        assertEquals(1, parent.getComponentCount());
        assertSame(child, parent.getComponent(0));
        // Element wrappers are value-equal (same StateNode), not reference-equal.
        assertEquals(parent.getPeer().getElement(), child.getPeer().getElement().getParent());
    }

    @Test
    @DisplayName("add at index inserts at that position in both lists")
    void addAtIndexInsertsAtThatPositionInBothLists() {
        Container parent = new Container();
        Component a = component();
        Component b = component();
        Component c = component();

        parent.add(a);
        parent.add(b);
        parent.add(c, 1); // a, c, b

        assertEquals(List.of(a, c, b), childrenOf(parent));
        assertEquals(a.getPeer().getElement(), parent.getPeer().getElement().getChild(0));
        assertEquals(c.getPeer().getElement(), parent.getPeer().getElement().getChild(1));
        assertEquals(b.getPeer().getElement(), parent.getPeer().getElement().getChild(2));
    }

    @Test
    @DisplayName("add with out-of-range index throws IllegalArgumentException")
    void addWithOutOfRangeIndexThrowsIllegalArgumentException() {
        Container parent = new Container();
        assertThrows(IllegalArgumentException.class, () -> parent.add(component(), 42));
        assertThrows(IllegalArgumentException.class, () -> parent.add(component(), -2));
    }

    @Test
    @DisplayName("adding self throws IllegalArgumentException")
    void addingSelfThrowsIllegalArgumentException() {
        Container parent = new Container();
        assertThrows(IllegalArgumentException.class, () -> parent.add(parent));
    }

    @Test
    @DisplayName("adding an ancestor throws IllegalArgumentException")
    void addingAnAncestorThrowsIllegalArgumentException() {
        Container grandparent = new Container();
        Container parent = new Container();
        grandparent.add(parent);
        assertThrows(IllegalArgumentException.class, () -> parent.add(grandparent));
    }

    @Test
    @DisplayName("getComponent with out-of-range index throws ArrayIndexOutOfBoundsException")
    void getComponentWithOutOfRangeIndexThrowsArrayIndexOutOfBoundsException() {
        Container parent = new Container();
        parent.add(component());
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> parent.getComponent(5));
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> parent.getComponent(-1));
    }

    @Test
    @DisplayName("remove by out-of-range index throws ArrayIndexOutOfBoundsException")
    void removeByOutOfRangeIndexThrowsArrayIndexOutOfBoundsException() {
        Container parent = new Container();
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> parent.remove(0));
    }

    @Test
    @DisplayName("adding a component already in another container reparents it")
    void addingAComponentAlreadyInAnotherContainerReparentsIt() {
        Container a = new Container();
        Container b = new Container();
        Component child = component();

        a.add(child);
        b.add(child);

        assertFalse(childrenOf(a).contains(child));
        assertSame(b, child.getParent());
        assertEquals(b.getPeer().getElement(), child.getPeer().getElement().getParent());
    }

    @Test
    @DisplayName("remove detaches from parent and peer DOM")
    void removeDetachesFromParentAndPeerDom() {
        Container parent = new Container();
        Component child = component();
        parent.add(child);

        parent.remove(child);

        assertNull(child.getParent());
        assertEquals(0, parent.getComponentCount());
        assertNull(child.getPeer().getElement().getParent());
    }

    @Test
    @DisplayName("remove by index matches remove by reference")
    void removeByIndexMatchesRemoveByReference() {
        Container parent = new Container();
        Component a = component();
        Component b = component();
        parent.add(a);
        parent.add(b);

        parent.remove(0);

        assertEquals(List.of(b), childrenOf(parent));
        assertNull(a.getParent());
    }

    @Test
    @DisplayName("remove of non-child is a no-op")
    void removeOfNonChildIsANoOp() {
        Container parent = new Container();
        Component stranger = component();

        parent.remove(stranger);

        assertEquals(0, parent.getComponentCount());
        assertNull(stranger.getParent());
    }

    @Test
    @DisplayName("removeAll clears everything")
    void removeAllClearsEverything() {
        Container parent = new Container();
        Component a = component();
        Component b = component();
        parent.add(a);
        parent.add(b);

        parent.removeAll();

        assertEquals(0, parent.getComponentCount());
        assertNull(a.getParent());
        assertNull(b.getParent());
        assertNull(a.getPeer().getElement().getParent());
        assertNull(b.getPeer().getElement().getParent());
    }

    @Test
    @DisplayName("getComponents returns a defensive copy")
    void getComponentsReturnsADefensiveCopy() {
        Container parent = new Container();
        Component child = component();
        parent.add(child);

        Component[] snapshot = parent.getComponents();
        parent.remove(child);

        assertEquals(1, snapshot.length);
        assertEquals(0, parent.getComponentCount());
    }

    @Test
    @DisplayName("isAncestorOf walks the parent chain")
    void isAncestorOfWalksTheParentChain() {
        Container grandparent = new Container();
        Container parent = new Container();
        Component child = component();
        grandparent.add(parent);
        parent.add(child);

        assertTrue(grandparent.isAncestorOf(parent));
        assertTrue(grandparent.isAncestorOf(child));
        assertTrue(parent.isAncestorOf(child));
        assertFalse(parent.isAncestorOf(grandparent));
        assertFalse(grandparent.isAncestorOf(null));
    }

    @Test
    @DisplayName("countComponents tracks getComponentCount")
    void countComponentsTracksGetComponentCount() {
        Container parent = new Container();
        parent.add(component());
        parent.add(component());

        assertEquals(parent.getComponentCount(), parent.countComponents());
    }

    @Test
    @DisplayName("getForeground walks the parent chain and returns null at the root when unset")
    void getForegroundWalksTheParentChainAndReturnsNullAtTheRootWhenUnset() {
        Container grandparent = new Container();
        Container parent = new Container();
        Component child = component();
        grandparent.add(parent);
        parent.add(child);

        assertNull(child.getForeground());
        grandparent.setForeground(Color.RED);
        assertEquals(Color.RED, child.getForeground());
        parent.setForeground(Color.BLUE);
        assertEquals(Color.BLUE, child.getForeground());

        // Orphan with nothing set → null at root (matches AWT).
        Component orphan = component();
        assertNull(orphan.getForeground());
    }

    @Test
    @DisplayName("getBackground walks the parent chain")
    void getBackgroundWalksTheParentChain() {
        Container parent = new Container();
        Component child = component();
        parent.add(child);

        assertNull(child.getBackground());
        parent.setBackground(Color.GREEN);
        assertEquals(Color.GREEN, child.getBackground());
    }

    @Test
    @DisplayName("getFont walks the parent chain")
    void getFontWalksTheParentChain() {
        Container parent = new Container();
        Component child = component();
        parent.add(child);

        Font font = new Font("Dialog", Font.PLAIN, 14);
        parent.setFont(font);
        assertSame(font, child.getFont());
    }

    @Test
    @DisplayName("getFont returns a non-null default when nothing is set")
    void getFontReturnsANonNullDefaultWhenNothingIsSet() {
        // Real Swing installs an L&F default font at construction, so getFont()
        // is non-null even before setFont and before attach; we run no L&F, so
        // the parent-walk falls back to CssConvert.DEFAULT_FONT (Dialog/plain/12)
        // rather than null. isFontSet() still reports the local-only truth.
        Component orphan = component();
        assertEquals(new Font(Font.DIALOG, Font.PLAIN, 12), orphan.getFont());
        assertFalse(orphan.isFontSet());
    }

    @Test
    @DisplayName("getFont survives the NetBeans deriveFont idiom without NPE")
    void getFontSurvivesTheNetBeansDeriveFontIdiomWithoutNpe() {
        // The idiom every Matisse initComponents() emits — used to NPE when
        // getFont() returned null. Regression guard for the jlawyer-shape crash.
        Component c = component();
        c.setFont(c.getFont().deriveFont(c.getFont().getStyle() | Font.BOLD, c.getFont().getSize() - 2f));
        assertTrue(c.getFont().isBold());
        assertEquals(10, c.getFont().getSize());
    }

    @Test
    @DisplayName("getLocale walks the parent chain")
    void getLocaleWalksTheParentChain() {
        Container parent = new Container();
        Component child = component();
        parent.add(child);

        parent.setLocale(Locale.FRENCH);
        assertEquals(Locale.FRENCH, child.getLocale());
    }

    @Test
    @DisplayName("getLocale throws IllegalComponentStateException on an orphan")
    void getLocaleThrowsIllegalComponentStateExceptionOnAnOrphan() {
        // Matches AWT; D_never_fail_on_gaps does not suppress Swing's own failure modes.
        Component orphan = component();
        assertThrows(IllegalComponentStateException.class, orphan::getLocale);
    }

    @Test
    @DisplayName("addPropertyChangeListener on Container delegates to Component's support")
    void addPropertyChangeListenerOnContainerDelegatesToComponentsSupport() {
        // Regression: the generator scaffolded addPropertyChangeListener overrides
        // on Container (for focus-traversal-keys bookkeeping we don't model) that
        // called onUnimplemented and silently dropped listeners. They now pass
        // through to Component.addPropertyChangeListener via super.
        Container parent = new Container();
        List<PropertyChangeEvent> events = new ArrayList<>();
        parent.addPropertyChangeListener(events::add);

        parent.setName("root");

        assertEquals(1, events.size());
        assertEquals("name", events.get(0).getPropertyName());
        assertEquals("root", events.get(0).getNewValue());
    }

    @Test
    @DisplayName("named addPropertyChangeListener on Container delegates to Component's support")
    void namedAddPropertyChangeListenerOnContainerDelegatesToComponentsSupport() {
        Container parent = new Container();
        List<PropertyChangeEvent> events = new ArrayList<>();
        parent.addPropertyChangeListener("foreground", events::add);

        parent.setName("ignored");
        parent.setForeground(Color.RED);

        assertEquals(1, events.size());
        assertEquals("foreground", events.get(0).getPropertyName());
    }

    @Test
    @DisplayName("sizing and alignment getters delegate to Component impl")
    void sizingAndAlignmentGettersDelegateToComponentImpl() {
        // Regression: generator-produced overrides used to return null (and WARN);
        // they now super-delegate to Component, so user-set sizes round-trip and
        // unset values return AWT defaults instead of null.
        Container parent = new Container();
        parent.setPreferredSize(new Dimension(123, 45));

        assertEquals(new Dimension(123, 45), parent.getPreferredSize());
        assertEquals(new Dimension(123, 45), parent.preferredSize()); // deprecated alias
        assertEquals(new Dimension(0, 0), parent.getMinimumSize());
        assertEquals(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE), parent.getMaximumSize());
        assertEquals(0.5f, parent.getAlignmentX());
        assertEquals(0.5f, parent.getAlignmentY());
    }

    @Test
    @DisplayName("paint and print recurse into children so user overrides on leaves fire")
    void paintAndPrintRecurseIntoChildrenSoUserOverridesOnLeavesFire() {
        // R_swing_is_truth: Swing compat layer must dispatch as AWT does. Even though
        // painting is effectively no-op (R_layouts_close_enough — browser paints), user code
        // may override paint() on a child to do something else.
        List<String> paintCalls = new ArrayList<>();
        Component child = new Component(new Div()) {
            @Override
            public void paint(java.awt.Graphics g) {
                paintCalls.add("paint");
            }

            @Override
            public void print(java.awt.Graphics g) {
                paintCalls.add("print");
            }
        };
        Container parent = new Container();
        parent.add(child);

        Graphics2D g = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics();
        parent.paint(g);
        parent.print(g);
        parent.update(g);  // AWT's update → paint

        assertEquals(List.of("paint", "print", "paint"), paintCalls);
    }

    @Test
    @DisplayName("peer attach fires addNotify, detach fires removeNotify across the subtree")
    void peerAttachFiresAddNotifyDetachFiresRemoveNotifyAcrossTheSubtree() {
        // The parent is a realisation root here (no emulator parent), so its
        // peer entering a live UI realises it, and Container's own cascade
        // carries addNotify down to the child — Vaadin's per-descendant attach
        // events are not what delivers this (D_component_displayable).
        // Attaching the parent's peer to a live UI must reach the child's
        // override; detaching must do the inverse.
        List<String> calls = new ArrayList<>();
        Component child = new Component(new Div()) {
            @Override
            public void addNotify() {
                calls.add("childAdd");
            }

            @Override
            public void removeNotify() {
                calls.add("childRemove");
            }
        };
        Container parent = new Container();
        parent.add(child);

        UI.getCurrent().getElement().appendChild(parent.getPeer().getElement());
        assertEquals(List.of("childAdd"), calls);

        parent.getPeer().getElement().removeFromParent();
        assertEquals(List.of("childAdd", "childRemove"), calls);
    }

    @Test
    @DisplayName("validateTree recurses into Container children and calls doLayout on self")
    void validateTreeRecursesIntoContainerChildrenAndCallsDoLayoutOnSelf() {
        List<String> calls = new ArrayList<>();
        Container childContainer = new Container() {
            @Override
            public void doLayout() {
                calls.add("childLayout");
            }
        };
        Container parent = new Container() {
            @Override
            public void doLayout() {
                calls.add("parentLayout");
            }
        };
        parent.add(childContainer);

        parent.validateTree();

        // Parent's doLayout runs before child Container's validateTree,
        // which in turn calls childContainer.doLayout.
        assertEquals(List.of("parentLayout", "childLayout"), calls);
    }

    @Test
    @DisplayName("getInsets returns empty for bare Container")
    void getInsetsReturnsEmptyForBareContainer() {
        Container parent = new Container();
        assertEquals(new Insets(0, 0, 0, 0), parent.getInsets());
        assertEquals(new Insets(0, 0, 0, 0), parent.insets()); // deprecated alias
        // Each call returns a fresh Insets (AWT contract lets callers mutate).
        parent.getInsets().top = 99;
        assertEquals(0, parent.getInsets().top);
    }

    @Test
    @DisplayName("applyComponentOrientation recurses into children")
    void applyComponentOrientationRecursesIntoChildren() {
        Container grandparent = new Container();
        Container parent = new Container();
        Component leaf = component();
        grandparent.add(parent);
        parent.add(leaf);

        grandparent.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);

        assertEquals(ComponentOrientation.RIGHT_TO_LEFT, grandparent.getComponentOrientation());
        assertEquals(ComponentOrientation.RIGHT_TO_LEFT, parent.getComponentOrientation());
        assertEquals(ComponentOrientation.RIGHT_TO_LEFT, leaf.getComponentOrientation());
    }

    @Test
    @DisplayName("list with indent prints self then children at indent+1")
    void listWithIndentPrintsSelfThenChildrenAtIndentPlus1() {
        Container parent = new Container();
        Component child = component();
        Component grandchild = component();
        Container nestedParent = new Container();
        nestedParent.add(grandchild);
        parent.add(child);
        parent.add(nestedParent);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        parent.list(new PrintStream(out), 0);

        List<String> lines = out.toString().stripTrailing().lines().toList();
        assertEquals(4, lines.size());
        // Indent depths: parent 0, direct children 1, grandchild 2.
        assertTrue(lines.get(0).startsWith("Container"), "got: " + lines.get(0));
        assertTrue(lines.get(1).startsWith(" "), "got: " + lines.get(1));
        assertTrue(lines.get(2).startsWith(" Container"), "got: " + lines.get(2));
        assertTrue(lines.get(3).startsWith("  "), "got: " + lines.get(3));
    }

    @Test
    @DisplayName("toString routes through paramString so a subclass override shows up")
    void toStringRoutesThroughParamStringSoASubclassOverrideShowsUp() {
        // Regression: toString() used to build its own string and never call
        // paramString(), leaving every paramString override in the tree
        // reachable only from another override's super call — no root caller
        // (R_no_vaadin_in_api limb 2). Invisible in the code, silent under test.
        Container c = new Container() {
            @Override
            protected String paramString() {
                return super.paramString() + ",mine=42";
            }
        };
        assertTrue(c.toString().contains(",mine=42"), "got: " + c);
    }

    @Test
    @DisplayName("adding a Window to a container throws, as AWT does")
    void addingAWindowToAContainerThrowsAsAwtDoes() {
        Container parent = new Container();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> parent.add(new Window((Frame) null)));
        assertEquals("adding a window to a container", ex.getMessage());
    }

    @Test
    @DisplayName("self-add reports AWT's own message, and a bad index outranks it")
    void selfAddReportsAwtsOwnMessageAndABadIndexOutranksIt() {
        Container parent = new Container();
        // AWT has no separate self-check: checkAddToSelf walks from `this`
        // upward, so a self-add lands on the ancestor message.
        assertEquals(
                "adding container's parent to itself",
                assertThrows(IllegalArgumentException.class, () -> parent.add(parent)).getMessage());
        // And the bounds check runs first, so the position wins the race.
        assertEquals(
                "illegal component position",
                assertThrows(IllegalArgumentException.class, () -> parent.add(parent, 99)).getMessage());
    }

    @Test
    @DisplayName("re-adding our own child past the shrunken end throws instead of clamping")
    void reAddingOurOwnChildPastTheShrunkenEndThrowsInsteadOfClamping() {
        // AWT re-checks the index after detaching comp from its old parent and
        // throws; clamping would silently accept a position AWT rejects (R_match_swing_errors).
        Container parent = new Container();
        Component child = component();
        parent.add(child);
        assertThrows(IllegalArgumentException.class, () -> parent.add(child, 1));
    }

    @Test
    @DisplayName("paramString returns empty instead of null")
    void paramStringReturnsEmptyInsteadOfNull() {
        // Regression: used to return null, breaking any toString that concatenated
        // super.paramString() from a subclass. paramString is protected; expose it
        // via a test-local subclass rather than reflection.
        class Exposing extends Container {
            String exposedParamString() {
                return paramString();
            }
        }
        assertEquals("", new Exposing().exposedParamString());
    }

    @Test
    @DisplayName("getComponentZOrder returns child index, -1 for non-children")
    void getComponentZOrderReturnsChildIndexMinus1ForNonChildren() {
        Container parent = new Container();
        Component a = component();
        Component b = component();
        parent.add(a);
        parent.add(b);

        assertEquals(0, parent.getComponentZOrder(a));
        assertEquals(1, parent.getComponentZOrder(b));
        assertEquals(-1, parent.getComponentZOrder(component())); // not a child
        assertEquals(-1, parent.getComponentZOrder(null));        // tolerant, matches AWT
    }

    @Test
    @DisplayName("setComponentZOrder moves existing child and mirrors into peer DOM")
    void setComponentZOrderMovesExistingChildAndMirrorsIntoPeerDom() {
        Container parent = new Container();
        Component a = component();
        Component b = component();
        Component c = component();
        parent.add(a);
        parent.add(b);
        parent.add(c); // a, b, c

        parent.setComponentZOrder(c, 0); // c, a, b

        assertEquals(List.of(c, a, b), childrenOf(parent));
        assertEquals(c.getPeer().getElement(), parent.getPeer().getElement().getChild(0));
        assertEquals(a.getPeer().getElement(), parent.getPeer().getElement().getChild(1));
        assertEquals(b.getPeer().getElement(), parent.getPeer().getElement().getChild(2));
    }

    @Test
    @DisplayName("setComponentZOrder treats index as position in post-removal list")
    void setComponentZOrderTreatsIndexAsPositionInPostRemovalList() {
        // AWT's subtle semantics: the target index is applied to the list
        // *after* the component has been pulled out, so moving index 0 to
        // index 2 in [a,b,c] yields [b,c,a] — not [b,a,c], which you'd get if
        // the index were interpreted against the original list.
        Container parent = new Container();
        Component a = component();
        Component b = component();
        Component c = component();
        parent.add(a);
        parent.add(b);
        parent.add(c); // a, b, c

        parent.setComponentZOrder(a, 2); // b, c, a

        assertEquals(List.of(b, c, a), childrenOf(parent));
    }

    @Test
    @DisplayName("setComponentZOrder to the same index is a no-op")
    void setComponentZOrderToTheSameIndexIsANoOp() {
        Container parent = new Container();
        Component a = component();
        Component b = component();
        parent.add(a);
        parent.add(b);

        parent.setComponentZOrder(a, 0);

        assertEquals(List.of(a, b), childrenOf(parent));
    }

    @Test
    @DisplayName("setComponentZOrder rejects same-reference, ancestors, null, and bad indices")
    void setComponentZOrderRejectsSameReferenceAncestorsNullAndBadIndices() {
        Container parent = new Container();
        Component child = component();
        parent.add(child);
        Container grandparent = new Container();
        grandparent.add(parent);

        assertThrows(NullPointerException.class, () -> parent.setComponentZOrder(null, 0));
        assertThrows(IllegalArgumentException.class, () -> parent.setComponentZOrder(parent, 0));
        assertThrows(IllegalArgumentException.class, () -> parent.setComponentZOrder(grandparent, 0));
        assertThrows(IllegalArgumentException.class, () -> parent.setComponentZOrder(child, -1));
        assertThrows(IllegalArgumentException.class, () -> parent.setComponentZOrder(child, 99));
    }

    /** One recorded {@code ContainerListener} callback: which one, and with what event. */
    private record Fired(String kind, ContainerEvent event) {
    }

    private static final class RecordingContainerListener implements ContainerListener {
        final List<Fired> events = new ArrayList<>();

        @Override
        public void componentAdded(ContainerEvent e) {
            events.add(new Fired("added", e));
        }

        @Override
        public void componentRemoved(ContainerEvent e) {
            events.add(new Fired("removed", e));
        }

        /** Just the callback names, in order — for the sequence-shaped assertions. */
        List<String> kinds() {
            return events.stream().map(Fired::kind).toList();
        }
    }

    @Test
    @DisplayName("add fires COMPONENT_ADDED after bookkeeping")
    void addFiresComponentAddedAfterBookkeeping() {
        Container parent = new Container();
        Component child = component();
        RecordingContainerListener l = new RecordingContainerListener();
        parent.addContainerListener(l);

        parent.add(child);

        assertEquals(1, l.events.size());
        Fired fired = l.events.get(0);
        assertEquals("added", fired.kind());
        assertEquals(ContainerEvent.COMPONENT_ADDED, fired.event().getID());
        assertSame(parent, fired.event().getContainer());
        assertSame(child, fired.event().getChild());
        // R_swing_is_truth ordering: event arrives after the child is fully linked.
        assertSame(parent, child.getParent());
        assertSame(child, parent.getComponent(0));
    }

    @Test
    @DisplayName("remove fires COMPONENT_REMOVED after unlink")
    void removeFiresComponentRemovedAfterUnlink() {
        Container parent = new Container();
        Component child = component();
        parent.add(child);
        RecordingContainerListener l = new RecordingContainerListener();
        parent.addContainerListener(l);

        parent.remove(child);

        assertEquals(1, l.events.size());
        Fired fired = l.events.get(0);
        assertEquals("removed", fired.kind());
        assertEquals(ContainerEvent.COMPONENT_REMOVED, fired.event().getID());
        assertSame(parent, fired.event().getContainer());
        assertSame(child, fired.event().getChild());
        assertNull(child.getParent());
    }

    @Test
    @DisplayName("reparenting fires REMOVED on old parent and ADDED on new parent")
    void reparentingFiresRemovedOnOldParentAndAddedOnNewParent() {
        Container a = new Container();
        Container b = new Container();
        Component child = component();
        a.add(child);
        RecordingContainerListener la = new RecordingContainerListener();
        RecordingContainerListener lb = new RecordingContainerListener();
        a.addContainerListener(la);
        b.addContainerListener(lb);

        b.add(child);

        assertEquals(1, la.events.size());
        assertEquals("removed", la.events.get(0).kind());
        assertEquals(1, lb.events.size());
        assertEquals("added", lb.events.get(0).kind());
    }

    @Test
    @DisplayName("removeAll fires one REMOVED per child")
    void removeAllFiresOneRemovedPerChild() {
        Container parent = new Container();
        Component a = component();
        Component b = component();
        parent.add(a);
        parent.add(b);
        RecordingContainerListener l = new RecordingContainerListener();
        parent.addContainerListener(l);

        parent.removeAll();

        assertEquals(List.of("removed", "removed"), l.kinds());
        // Order matches the tail-first loop in removeAll — b (last added) goes first.
        assertSame(b, l.events.get(0).event().getChild());
        assertSame(a, l.events.get(1).event().getChild());
    }

    @Test
    @DisplayName("setComponentZOrder does not fire container events")
    void setComponentZOrderDoesNotFireContainerEvents() {
        // AWT documents setComponentZOrder as the event-free reorder path —
        // that's its whole reason for existing alongside add(comp, index).
        Container parent = new Container();
        Component a = component();
        Component b = component();
        parent.add(a);
        parent.add(b);
        RecordingContainerListener l = new RecordingContainerListener();
        parent.addContainerListener(l);

        parent.setComponentZOrder(a, 1);

        assertEquals(List.of(), l.kinds());
    }

    @Test
    @DisplayName("removeContainerListener stops further events")
    void removeContainerListenerStopsFurtherEvents() {
        Container parent = new Container();
        RecordingContainerListener l = new RecordingContainerListener();
        parent.addContainerListener(l);
        parent.removeContainerListener(l);

        parent.add(component());

        assertEquals(0, l.events.size());
    }

    @Test
    @DisplayName("getContainerListeners returns registered listeners, empty array when none")
    void getContainerListenersReturnsRegisteredListenersEmptyArrayWhenNone() {
        Container parent = new Container();
        assertEquals(0, parent.getContainerListeners().length);

        RecordingContainerListener l = new RecordingContainerListener();
        parent.addContainerListener(l);
        assertSame(l, assertSingle(parent.getContainerListeners()));
    }

    @Test
    @DisplayName("null listener is silently ignored on add and remove")
    void nullListenerIsSilentlyIgnoredOnAddAndRemove() {
        // AWT's own behaviour — not an error, just a no-op. Avoids forcing
        // callers to null-check before forwarding listener params.
        Container parent = new Container();
        parent.addContainerListener(null);
        parent.removeContainerListener(null);
        assertEquals(0, parent.getContainerListeners().length);
    }

    @Test
    @DisplayName("multiple listeners all receive the event")
    void multipleListenersAllReceiveTheEvent() {
        Container parent = new Container();
        RecordingContainerListener a = new RecordingContainerListener();
        RecordingContainerListener b = new RecordingContainerListener();
        parent.addContainerListener(a);
        parent.addContainerListener(b);

        parent.add(component());

        assertEquals(1, a.events.size());
        assertEquals(1, b.events.size());
    }

    @Test
    @DisplayName("listener that removes itself during dispatch does not CME")
    void listenerThatRemovesItselfDuringDispatchDoesNotCme() {
        // The whole reason we back the listener list with CopyOnWriteArrayList:
        // a listener that mutates the list during its own callback (common in
        // "one-shot" subscribers) must not trip ConcurrentModificationException.
        Container parent = new Container();
        ContainerListener oneShot = new ContainerListener() {
            @Override
            public void componentAdded(ContainerEvent e) {
                parent.removeContainerListener(this);
            }

            @Override
            public void componentRemoved(ContainerEvent e) {
            }
        };
        parent.addContainerListener(oneShot);

        parent.add(component()); // fires; oneShot unregisters itself
        parent.add(component()); // second add — oneShot should no longer fire

        assertEquals(0, parent.getContainerListeners().length);
    }

    @Test
    @DisplayName("focus-traversal booleans default to false without warning")
    void focusTraversalBooleansDefaultToFalseWithoutWarning() {
        // Regression: these used to call onUnimplemented and log WARN on every
        // query. AWT's own default for all four is false until the matching
        // setter is called, so we now return false silently — honest state,
        // not a gap. Setters remain stubs (R_infra_not_surface).
        Container parent = new Container();
        assertFalse(parent.isFocusCycleRoot());
        assertFalse(parent.isFocusCycleRoot(parent));
        assertFalse(parent.isFocusTraversalPolicySet());
        assertFalse(parent.isFocusTraversalPolicyProvider());
        assertFalse(parent.areFocusTraversalKeysSet(0));
    }

    @Test
    @DisplayName("focus-traversal getters return empty or null without warning")
    void focusTraversalGettersReturnEmptyOrNullWithoutWarning() {
        // Pairs with the booleans above: when is*Set is false and we don't
        // model focus traversal (R_infra_not_surface), the matching getters should report
        // "nothing here" silently instead of logging WARN + null. Empty Set
        // for keys, null for policy — honest answers callers can still branch
        // on without an AWT contract violation (AWT permits null policy on a
        // non-focus-cycle-root; ours is never a cycle root).
        Container parent = new Container();
        assertNull(parent.getFocusTraversalPolicy());
        assertTrue(parent.getFocusTraversalKeys(0).isEmpty());
        assertTrue(parent.getFocusTraversalKeys(1).isEmpty());
    }

    @Test
    @DisplayName("getCursor walks the parent chain and falls back to default cursor")
    void getCursorWalksTheParentChainAndFallsBackToDefaultCursor() {
        Container parent = new Container();
        Component child = component();
        parent.add(child);

        assertEquals(Cursor.getDefaultCursor(), child.getCursor());
        Cursor hand = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
        parent.setCursor(hand);
        assertSame(hand, child.getCursor());
    }

    private static final class RecordingHierarchyListener implements HierarchyListener {
        final List<HierarchyEvent> events = new ArrayList<>();

        @Override
        public void hierarchyChanged(HierarchyEvent e) {
            events.add(e);
        }
    }

    @Test
    @DisplayName("add fires HIERARCHY_CHANGED with PARENT_CHANGED on the child")
    void addFiresHierarchyChangedWithParentChangedOnTheChild() {
        Container parent = new Container();
        Component child = component();
        RecordingHierarchyListener l = new RecordingHierarchyListener();
        child.addHierarchyListener(l);

        parent.add(child);

        assertEquals(1, l.events.size());
        HierarchyEvent e = l.events.get(0);
        assertEquals(HierarchyEvent.HIERARCHY_CHANGED, e.getID());
        assertEquals((long) HierarchyEvent.PARENT_CHANGED, e.getChangeFlags());
        // source is the component receiving the event; changed is the one
        // whose parent actually changed. For a direct add both are the child.
        assertSame(child, e.getComponent());
        assertSame(child, e.getChanged());
        assertSame(parent, e.getChangedParent());
    }

    @Test
    @DisplayName("remove fires HIERARCHY_CHANGED with PARENT_CHANGED on the removed child")
    void removeFiresHierarchyChangedWithParentChangedOnTheRemovedChild() {
        Container parent = new Container();
        Component child = component();
        parent.add(child);
        RecordingHierarchyListener l = new RecordingHierarchyListener();
        child.addHierarchyListener(l);

        parent.remove(child);

        assertEquals(1, l.events.size());
        HierarchyEvent e = l.events.get(0);
        assertEquals(HierarchyEvent.HIERARCHY_CHANGED, e.getID());
        assertEquals((long) HierarchyEvent.PARENT_CHANGED, e.getChangeFlags());
        // On remove, changedParent is the old parent — AWT passes it so
        // listeners can tell which container the component used to belong to,
        // even though child.parent is already null by the time the event fires.
        assertSame(parent, e.getChangedParent());
        assertNull(child.getParent());
    }

    @Test
    @DisplayName("add recurses HIERARCHY_CHANGED into descendants")
    void addRecursesHierarchyChangedIntoDescendants() {
        // Attaching a whole subtree: every descendant sees the event, each with
        // source=itself but changed=the component that was actually reparented.
        Component grandchild = component();
        Container subtree = new Container();
        subtree.add(grandchild);
        Container parent = new Container();
        RecordingHierarchyListener lSubtree = new RecordingHierarchyListener();
        RecordingHierarchyListener lGrandchild = new RecordingHierarchyListener();
        subtree.addHierarchyListener(lSubtree);
        grandchild.addHierarchyListener(lGrandchild);

        parent.add(subtree);

        assertEquals(1, lSubtree.events.size());
        assertEquals(1, lGrandchild.events.size());
        // Source is the listener's own component.
        assertSame(subtree, lSubtree.events.get(0).getComponent());
        assertSame(grandchild, lGrandchild.events.get(0).getComponent());
        // Changed is the one that was actually reparented (subtree) — grandchild's
        // direct parent didn't change, but the hierarchy it's in did.
        assertSame(subtree, lSubtree.events.get(0).getChanged());
        assertSame(subtree, lGrandchild.events.get(0).getChanged());
        assertSame(parent, lGrandchild.events.get(0).getChangedParent());
    }

    @Test
    @DisplayName("fireHierarchyEvent short-circuits when nobody is listening")
    void fireHierarchyEventShortCircuitsWhenNobodyIsListening() {
        // No HierarchyListener anywhere — the fire path must not allocate an
        // event. Hard to observe directly; we settle for "add doesn't throw and
        // behaves normally" and trust the getListenerCount guard.
        Container parent = new Container();
        Component child = component();
        parent.add(child);  // no HierarchyListener registered — should be silent
        assertSame(parent, child.getParent());
    }

    @Test
    @DisplayName("setFocusTraversalKeys empty set is silent")
    void setFocusTraversalKeysEmptySetIsSilent() {
        // An empty set means "reset to browser default" — which is what
        // we do natively (R_infra_not_surface — no focus traversal modeling, browser
        // picks). Silent. Non-empty still WARNs.
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;
        try {
            new Container().setFocusTraversalKeys(0, Set.of());
            assertEquals(0, warnings.size());
        } finally {
            EHelper.warnHook = (Consumer<String>) msg -> {
            };
        }
    }

    @Test
    @DisplayName("setFocusTraversalKeys non-empty set WARNs")
    void setFocusTraversalKeysNonEmptySetWarns() {
        List<String> warnings = new ArrayList<>();
        EHelper.warnHook = warnings::add;
        try {
            new Container().setFocusTraversalKeys(0, Set.of(AWTKeyStroke.getAWTKeyStroke('X')));
            assertEquals(1, warnings.size());
            assertTrue(warnings.get(0).contains("setFocusTraversalKeys"), warnings.get(0));
        } finally {
            EHelper.warnHook = (Consumer<String>) msg -> {
            };
        }
    }

    // --- setLayout drops the outgoing manager's container CSS ---------

    @Test
    @DisplayName("setLayout clears the previous manager's CSS but lays out nothing")
    void setLayoutClearsThePreviousManagersCssButLaysOutNothing() {
        // AWT's setLayout only invalidates, and we keep that quirk — so no new
        // CSS until validate(). What we do NOT keep is the old manager's CSS:
        // in a browser, stale display:flex over children carrying the incoming
        // BorderLayout's grid-area renders scrambled, where a cleared container
        // renders a coherent block flow.
        Container c = new Container();
        c.setLayout(new FlowLayout());
        c.validate();
        assertEquals("flex", c.getPeer().getElement().getStyle().get("display"));

        c.setLayout(new BorderLayout());
        assertNull(c.getPeer().getElement().getStyle().get("display"));
        assertNull(c.getPeer().getElement().getStyle().get("flex-wrap"));
        assertNull(c.getPeer().getElement().getStyle().get("gap"));

        // …and the incoming manager still lands on the next validate.
        c.validate();
        assertEquals("grid", c.getPeer().getElement().getStyle().get("display"));
    }

    @Test
    @DisplayName("setLayout null clears the CSS too")
    void setLayoutNullClearsTheCssToo() {
        Container c = new Container();
        c.setLayout(new FlowLayout());
        c.validate();
        assertEquals("flex", c.getPeer().getElement().getStyle().get("display"));

        c.setLayout(null);
        assertNull(c.getLayout());
        assertNull(c.getPeer().getElement().getStyle().get("display"));
        c.validate();   // no manager — stays clear rather than reinstating flex
        assertNull(c.getPeer().getElement().getStyle().get("display"));
    }

    @Test
    @DisplayName("re-setting the same manager instance is not a reset")
    void reSettingTheSameManagerInstanceIsNotAReset() {
        // Guard against a reset that outlives its purpose: same instance in
        // means the CSS on the element is already the right CSS.
        Container c = new Container();
        FlowLayout fl = new FlowLayout();
        c.setLayout(fl);
        c.validate();
        c.setLayout(fl);
        assertEquals("flex", c.getPeer().getElement().getStyle().get("display"));
    }

    @Test
    @DisplayName("first setLayout does not touch the element")
    void firstSetLayoutDoesNotTouchTheElement() {
        // Every ctor's setLayout is a first one; there is nothing of ours on the
        // element yet, so the reset must not fire (it would clobber a peer
        // surrogate's own ctor-time CSS — SJPanel writes FlowLayout eagerly).
        Container c = new Container();
        c.getPeer().getElement().getStyle().set("display", "flex");
        c.setLayout(new BorderLayout());
        assertEquals("flex", c.getPeer().getElement().getStyle().get("display"));
    }
}
