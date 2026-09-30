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


package vaadinx.swing;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import vaadinx.AbstractKaribuTest;
import vaadinx.EmulatorContext;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A {@code JTextArea} used as a log console by a {@code doInBackground()}-shaped worker
 * (SD_background_model_hop). The JDK documents {@code AbstractDocument.insertString} as thread
 * safe, so this is the strongest case: the migrator's {@code DocumentListener}s run on the
 * worker, as on the desktop, and the peer write hops onto the UI thread.
 */
class JTextAreaBackgroundModelTest extends AbstractKaribuTest {

    /** Runs {@code body} on a thread carrying this session's {@link EmulatorContext}, off the Karibu lock. */
    private static void runOnWorker(Runnable body) {
        EmulatorContext ctx = EmulatorContext.get();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(() -> ctx.run(() -> {
            try {
                body.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        }), "log-worker");
        VaadinSession session = VaadinSession.getCurrent();
        UI ui = UI.getCurrent();
        session.unlock();
        try {
            t.start();
            t.join(10_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            session.lock();
            VaadinSession.setCurrent(session);
            UI.setCurrent(ui);
        }
        if (t.isAlive()) fail("the worker never returned");
        if (failure.get() != null) throw new AssertionError("worker failed", failure.get());
    }

    /** Records each insert's text and the UI current when the migrator's listener heard it. */
    private static List<String> recordInserts(JTextArea area, List<UI> uis) {
        List<String> seen = new CopyOnWriteArrayList<>();
        area.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) {
                try {
                    seen.add(e.getDocument().getText(e.getOffset(), e.getLength()));
                } catch (BadLocationException ex) {
                    throw new AssertionError(ex);
                }
                uis.add(UI.getCurrent());
            }
            @Override public void removeUpdate(DocumentEvent e) { }
            @Override public void changedUpdate(DocumentEvent e) { }
        });
        return seen;
    }

    @Test
    @DisplayName("constructed and written on a worker, never attached: fires on the worker, as the JDK does")
    void detachedAreaBuiltOnWorker() {
        AtomicReference<JTextArea> built = new AtomicReference<>();
        List<UI> uis = new CopyOnWriteArrayList<>();
        AtomicReference<List<String>> seen = new AtomicReference<>();

        runOnWorker(() -> {
            JTextArea area = new JTextArea();
            seen.set(recordInserts(area, uis));
            area.append("one\n");
            area.append("two\n");
            built.set(area);
        });

        assertEquals(List.of("one\n", "two\n"), seen.get());
        assertNull(uis.get(0));
        assertEquals("one\ntwo\n", built.get().getText());
        assertEquals("one\ntwo\n", ((TextArea) built.get().getPeer()).getValue());
    }

    @Test
    @DisplayName("attached, appended to on a worker: fires on the worker, the peer write still lands")
    void attachedAreaAppendedOnWorker() {
        JTextArea area = new JTextArea();
        UI.getCurrent().add(area.getPeer());
        List<UI> uis = new CopyOnWriteArrayList<>();
        List<String> seen = recordInserts(area, uis);

        runOnWorker(() -> {
            area.append("one\n");
            try {
                area.getDocument().insertString(0, "zero\n", null);
            } catch (BadLocationException e) {
                throw new AssertionError(e);
            }
        });

        assertEquals(List.of("one\n", "zero\n"), seen);
        assertNull(uis.get(0), "the JDK fires Document events on the mutating thread");
        assertEquals("zero\none\n", area.getText());
        assertEquals("zero\none\n", ((TextArea) area.getPeer()).getValue());
    }
}
