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

package com.example.app;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.server.ErrorEvent;
import com.vaadin.flow.server.ErrorHandler;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Logs every uncaught UI error under a reference number and shows the user that number — the
 * guide's Phase 4 says what to put here instead if your Swing app had a handler of its own.
 * A bean, not an SPI line: Vaadin registers every {@code VaadinServiceInitListener} bean itself.
 */
@Component
public class AppErrorHandler implements VaadinServiceInitListener, ErrorHandler {

    /** Not {@code static}: the context holds one instance per deployment, which is the lifetime it needs. */
    private final AtomicInteger errorId = new AtomicInteger();

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addSessionInitListener(e -> e.getSession().setErrorHandler(this));
    }

    @Override
    public void error(ErrorEvent event) {
        int id = errorId.incrementAndGet();
        // The stack trace stays in the server log: it leaks class names, paths and library versions.
        LoggerFactory.getLogger(AppErrorHandler.class).error("Unhandled UI error #" + id, event.getThrowable());
        // Guarded: a JVM-wide handler of yours may call this where there is no UI.
        if (UI.getCurrent() != null) {
            UI.getCurrent().access(() -> Notification.show(
                    "An internal error occurred (ref #" + id + "). Contact support."));
        }
    }
}
