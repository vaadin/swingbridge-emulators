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

package com.vaadin.swingbridge.surrogates;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every surrogate knows the session its off-thread writes hop through, without reading
 * anything the UI thread writes (SHelper.runOnSession): it declares or inherits the
 * {@code volatile} {@code hopSession} field, captures the current session at construction,
 * and captures it at first attach when it was built where none was current.
 */
class SessionCaptureTest extends AbstractKaribuTest {

    @Test
    @DisplayName("every surrogate has a volatile hopSession field")
    void everySurrogateHasTheField() throws Exception {
        List<String> missing = new ArrayList<>();
        for (Class<?> type : SurrogateTypes.all()) {
            Field f = findField(type);
            if (f == null || !Modifier.isVolatile(f.getModifiers()) || f.getType() != VaadinSession.class) {
                missing.add(type.getName());
            }
        }
        assertEquals(List.of(), missing,
                "declare 'private volatile VaadinSession hopSession' in each surrogate root, captured at construction and on attach");
    }

    @Test
    @DisplayName("a surrogate built with a session current captures it at construction")
    void capturesAtConstruction() throws Exception {
        VaadinSession session = VaadinSession.getCurrent();
        List<String> wrong = new ArrayList<>();
        for (Class<?> type : SurrogateTypes.all()) {
            Constructor<?> ctor = SurrogateTypes.noArgConstructor(type);
            if (ctor == null) continue;
            Component c = (Component) ctor.newInstance();
            if (SHelper.sessionOf(c) != session) wrong.add(type.getName());
        }
        assertEquals(List.of(), wrong);
    }

    @Test
    @DisplayName("a surrogate built with no session captures it at first attach")
    void capturesAtAttach() throws Exception {
        List<String> wrong = new ArrayList<>();
        int probed = 0;
        for (Class<?> type : SurrogateTypes.all()) {
            Constructor<?> ctor = SurrogateTypes.noArgConstructor(type);
            if (ctor == null) continue;
            Component c = constructWithNoSession(ctor);
            if (c == null) continue;
            if (SHelper.sessionOf(c) != null) {
                wrong.add(type.getName() + ": captured a session on a thread with none");
                continue;
            }
            try {
                UI.getCurrent().add(c);
            } catch (RuntimeException notAttachable) {
                continue;
            }
            probed++;
            if (SHelper.sessionOf(c) != VaadinSession.getCurrent()) {
                wrong.add(type.getName() + ": did not capture the session at attach");
            }
            UI.getCurrent().remove(c);
        }
        assertTrue(probed > 20, "attached almost nothing: " + probed);
        assertEquals(List.of(), wrong);
    }

    private static Component constructWithNoSession(Constructor<?> ctor) throws InterruptedException {
        AtomicReference<Component> built = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                built.set((Component) ctor.newInstance());
            } catch (ReflectiveOperationException | RuntimeException e) {
                // Not constructible off the UI thread; the pinning test reports those.
            }
        });
        t.start();
        t.join(5_000);
        return built.get();
    }

    private static Field findField(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                return c.getDeclaredField(SHelper.SESSION_FIELD);
            } catch (NoSuchFieldException e) {
                // Try the superclass.
            }
        }
        return null;
    }
}
