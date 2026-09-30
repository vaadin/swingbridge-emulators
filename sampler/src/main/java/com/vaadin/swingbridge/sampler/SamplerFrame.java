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

import com.vaadin.swingbridge.sampler.SamplerCatalogue.Demo;
import vaadinx.awt.BorderLayout;
import vaadinx.swing.JComponent;
import vaadinx.swing.JFrame;
import vaadinx.swing.JPanel;
import vaadinx.swing.MainWindow;

/**
 * Pure-emulator Sampler shell — a single {@code @MainWindow} JFrame mounted at
 * {@code /} by {@link SamplerRoute}, wiring two nav affordances over one
 * {@link SamplerCatalogue} to a content panel that swaps demos:
 *
 * <pre>
 * NORTH   SamplerJumpTo   flat filterable dropdown of all demos
 * WEST    SamplerNav      collapsible category groups
 * CENTER  content         the selected demo, rebuilt per navigation
 * </pre>
 *
 * <p>Both nav components report selection up here; the shell does the swap
 * and pushes the new selection back into the *other* one so the two never
 * disagree about where the user is.
 *
 * <p>Content swap = {@code removeAll() + add() + revalidate() + repaint()} —
 * deliberately the plain Swing idiom, so a shaky emulator revalidate path shows
 * up here first.
 *
 * <p>Demo lifecycle hooks ride on {@link vaadinx.swing.event.AncestorListener}:
 * a demo {@link JPanel} registers a listener in its ctor, gets
 * {@code ancestorAdded} when the shell adds it to the content panel, and
 * {@code ancestorRemoved} when the next swap removes it. Default-button binding
 * and Timer/SwingWorker teardown both ride this hook — see {@link FormPanel} for
 * the canonical shape.
 */
@MainWindow
public class SamplerFrame extends JFrame {

    private final JPanel content = new JPanel(new BorderLayout());
    private final SamplerNav nav = new SamplerNav(this::select);
    private final SamplerJumpTo jumpTo = new SamplerJumpTo(this::select);

    public SamplerFrame() {
        super("Swing-on-Vaadin Sampler");
        setLayout(new BorderLayout());
        add(jumpTo, BorderLayout.NORTH);
        add(nav, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        select(SamplerCatalogue.ALL.get(0));
    }

    private void select(Demo demo) {
        nav.reveal(demo);
        jumpTo.select(demo);
        showDemo(demo.factory().get());
    }

    private void showDemo(JComponent demo) {
        content.removeAll();
        content.add(demo, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }
}
