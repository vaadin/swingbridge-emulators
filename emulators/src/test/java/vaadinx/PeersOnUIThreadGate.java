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

package vaadinx;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Fails every test that builds a lazy emulator's Vaadin peer with no UI current
 * ({@link EHelper#onPeerBuiltOffUIThread}), so a new raw reach reddens the build instead of
 * logging a WARN once per JVM. Registered for every test by Jupiter's extension autodetection
 * ({@code junit-platform.properties}); the class-level check catches what a {@code @BeforeAll}
 * or {@code @AfterAll} builds.
 */
public final class PeersOnUIThreadGate
        implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback, AfterEachCallback {

    private static final ExtensionContext.Namespace NS =
            ExtensionContext.Namespace.create(PeersOnUIThreadGate.class);

    @Override
    public void beforeAll(ExtensionContext context) {
        snapshot(context);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        snapshot(context);
    }

    @Override
    public void afterEach(ExtensionContext context) {
        check(context);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        check(context);
    }

    private static void snapshot(ExtensionContext context) {
        context.getStore(NS).put(context.getUniqueId(), EHelper.peersBuiltOffUIThread());
    }

    private static void check(ExtensionContext context) {
        Integer before = context.getStore(NS).remove(context.getUniqueId(), Integer.class);
        int built = EHelper.peersBuiltOffUIThread() - before;
        if (built > 0) {
            // Re-baseline the enclosing classes' counts, so they do not fail a second time for a
            // reach one of their tests already failed for.
            for (ExtensionContext c = context.getParent().orElse(null); c != null; c = c.getParent().orElse(null)) {
                if (c.getStore(NS).get(c.getUniqueId()) != null) snapshot(c);
            }
            throw new AssertionError(built + " lazy peer(s) built with no UI current: an emulator's peer "
                    + "was reached directly, off the UI thread, where a withPeer write would have "
                    + "queued. The cause is the most recent reach.",
                    EHelper.lastPeerBuiltOffUIThread());
        }
    }
}
