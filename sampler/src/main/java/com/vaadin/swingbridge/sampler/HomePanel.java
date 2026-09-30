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
import vaadinx.awt.FlowLayout;
import vaadinx.swing.BorderFactory;
import vaadinx.swing.JLabel;
import vaadinx.swing.JPanel;

/**
 * Landing-page panel for the pure-emulator Sampler shell — one class per
 * nav entry, like every other demo target. Placeholder today for richer
 * content (per-demo summaries, links, shortcut hints) once Sampler
 * graduates from snapshot to load-bearing testbed.
 */
public class HomePanel extends JPanel {

    public HomePanel() {
        super(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel rows = new JPanel(new FlowLayout(FlowLayout.LEADING, 8, 8));
        rows.add(new JLabel("Swing-on-Vaadin Sampler — pure-emulator shell"));
        rows.add(new JLabel("Pick a demo from the left navigation, or type into \u201cJump to\u201d above."));
        add(rows, BorderLayout.NORTH);
    }
}
