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

package com.ca.ui;

import com.vaadin.flow.router.PreserveOnRefresh;
import com.vaadin.flow.router.Route;
import vaadinx.swing.app.MainWindowRoute;

/** Mounts the app at {@code /}; Vaadin does not discover {@code @MainWindow} on its own. */
@Route("")
@PreserveOnRefresh
public class AppRoute extends MainWindowRoute {
    @Override
    protected void bootstrap() {
        Main.mainUI();
    }
}
