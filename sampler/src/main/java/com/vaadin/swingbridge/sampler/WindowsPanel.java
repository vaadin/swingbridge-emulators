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

import vaadinx.awt.BorderLayout;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.Box;
import vaadinx.swing.BoxLayout;
import vaadinx.swing.JButton;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;
import vaadinx.swing.JProgressBar;
import vaadinx.swing.JWindow;
import vaadinx.swing.Timer;

/**
 * {@link JWindow} demo — undecorated top-level windows. Two sub-demos,
 * each covering one of JWindow's two real-world shapes so the exit-gate
 * test can assert "user path emits no stub WARNs":
 *
 * <ol>
 *   <li><b>Splash screen</b> — centered ({@code setLocationRelativeTo(null)}),
 *       label + progress bar, a repeating {@link Timer} stepping the
 *       progress, auto-{@code dispose()} at 100%. The classic app-startup
 *       idiom migrated verbatim.</li>
 *   <li><b>Toast</b> — explicit corner placement via {@code setBounds}
 *       (the Window-geometry surface mapping onto the Dialog overlay's
 *       top/left/width/height), one-shot Timer auto-dismissal.</li>
 * </ol>
 *
 * <p>Both windows render chrome-less per SJWindow's always-on
 * {@code emul-undecorated} class: no header, no padding, no border
 * radius — the box-shadow stays (documented R_layouts_close_enough divergence; a shadowless
 * rect over live page content is illegible).
 */
public class WindowsPanel extends JPanel {

    public WindowsPanel() {
        super();
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JButton showSplash = new JButton("Show splash (auto-closes)");
        showSplash.addActionListener(e -> showSplash());
        add(demoSection("Demo 1 — splash screen (centered, progress-driven)", showSplash));
        add(Box.createVerticalStrut(8));

        JButton showToast = new JButton("Show toast (bottom-right, 2.5s)");
        showToast.addActionListener(e -> showToast());
        add(demoSection("Demo 2 — toast (bottom-right via Toolkit.getScreenSize())", showToast));
    }

    /**
     * Splash idiom: centered undecorated window with a progress bar; a
     * repeating Timer steps 20% every 250ms and disposes at 100%.
     */
    private void showSplash() {
        JWindow splash = new JWindow();
        JPanel body = new JPanel(new BorderLayout(0, 8));
        body.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        body.add(new JLabel("Loading Swing-on-Vaadin Sampler…"), BorderLayout.NORTH);
        JProgressBar progress = new JProgressBar(0, 100);
        progress.setStringPainted(true);
        body.add(progress, BorderLayout.CENTER);
        splash.add(body);
        splash.setSize(360, 110);
        splash.setLocationRelativeTo(null);
        splash.setVisible(true);

        Timer timer = new Timer(250, null);
        timer.addActionListener(e -> {
            int v = progress.getValue() + 20;
            progress.setValue(Math.min(100, v));
            if (v >= 100) {
                timer.stop();
                splash.dispose();
            }
        });
        timer.start();
    }

    /**
     * Toast idiom: bottom-right anchoring computed the classic Swing way
     * from {@code Toolkit.getScreenSize()} — which reports the browser
     * viewport per D_toolkit_screen_size, so the math lands on the visible page. One-shot
     * Timer dismissal.
     */
    private void showToast() {
        JWindow toast = new JWindow();
        JPanel body = new JPanel(new BorderLayout());
        body.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        body.add(new JLabel("Saved — this toast dismisses itself."), BorderLayout.CENTER);
        toast.add(body);
        java.awt.Dimension screen = vaadinx.awt.Toolkit.getDefaultToolkit().getScreenSize();
        toast.setBounds(screen.width - 320 - 24, screen.height - 48 - 24, 320, 48);
        toast.setVisible(true);

        Timer dismiss = new Timer(2500, e -> toast.dispose());
        dismiss.setRepeats(false);
        dismiss.start();
    }

    private static JPanel demoSection(String labelText, vaadinx.swing.JComponent body) {
        JPanel wrap = new JPanel(new BorderLayout(0, 4));
        JLabel label = new JLabel(labelText);
        label.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
        wrap.add(label, BorderLayout.NORTH);
        wrap.add(body, BorderLayout.CENTER);
        return wrap;
    }
}
