/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: 0BSD
 *
 * A seed file: copy it into your app and license the result as you choose. This
 * notice need not be kept.
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY
 * SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION
 * OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN
 * CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package com.vaadin.swingbridge.testapps.jlawyershape.swing;

import com.github.mvysny.vaadinboot.VaadinBoot;
import vaadinx.swing.SwingUtilities;

/**
 * The two entry points of the migrated app — the guide's Phase 3 split. Move your old
 * {@code main()}'s process-level work into {@link #main} and its frame construction into
 * {@link #mainUI}.
 */
public final class Main {

    /** Once per JVM. Everything here runs before the first request — {@code run()} blocks until shutdown. */
    public static void main(String[] args) throws Exception {
        // your process-level start-up work goes here, before run()
        new VaadinBoot()
                .useVirtualThreadsIfAvailable(false)  // not optional: the blocking-dialog executor runs its
                .run();                               // own virtual threads on platform request threads
    }

    /** Once per browser tab, from {@link AppRoute}. */
    public static void mainUI() {
        // The in-memory store is the frame's own, so seeding it is per-launch work.
        SwingUtilities.invokeLater(() -> {
            CaseStore store = new CaseStore();
            store.seed();
            new MainFrame(store).setVisible(true);
        });
    }
}
