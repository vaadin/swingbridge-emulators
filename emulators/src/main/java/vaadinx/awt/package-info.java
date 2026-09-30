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

/**
 * AWT-layer emulation: {@code vaadinx.awt} reproduces the {@link java.awt.Component}
 * / {@link java.awt.Container} hierarchy the {@code vaadinx.swing} emulators build
 * on. Each emulator holds one Vaadin <em>peer</em> by composition (D_emulator_surrogate_split) and drives
 * it to match the Swing-side state it stores (R_swing_is_truth); {@link vaadinx.EHelper} holds the
 * hand-written plumbing shared across the generated scaffolds.
 *
 * <p>This is the overview, not the authority — see {@code CLAUDE.md} and
 * {@code emulators/architecture.md} for the whole picture. It lives at package
 * level because the hierarchy root {@link vaadinx.awt.Component} is a regenerated
 * scaffold: a class-level home for the contract below would be clobbered on the
 * next generator run.
 *
 * <p><strong>UI-thread-confined.</strong> Every emulator (and the Vaadin peer
 * beneath it) is a Vaadin component, so all methods must run on the session's UI
 * thread — the emulated EDT. Migrated code reaching in from a background thread
 * marshals through {@link vaadinx.swing.SwingUtilities#invokeLater} / {@code
 * UI.access}, exactly as real Swing requires the EDT; peer→Swing event callbacks
 * re-enter on the UI thread through {@link vaadinx.EHelper#callSwing} (R_callswing_envelope). The
 * concurrency primitives in {@code vaadinx.swing} ({@link vaadinx.swing.Timer},
 * {@link vaadinx.swing.SwingWorker}, {@link vaadinx.swing.SwingUtilities}) are the
 * deviations — they self-marshal delivery onto the UI thread — and carry their own
 * thread contracts.
 */
package vaadinx.awt;
