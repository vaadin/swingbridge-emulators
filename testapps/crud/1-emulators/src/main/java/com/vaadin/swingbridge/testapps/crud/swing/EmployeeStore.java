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

package com.vaadin.swingbridge.testapps.crud.swing;

import javax.swing.event.EventListenerList;
import java.util.ArrayList;
import vaadinx.util.Calendar;
import java.util.Date;
import java.util.EventListener;
import java.util.List;

public final class EmployeeStore {

    public interface Listener extends EventListener {
        void itemsChanged();
    }

    private final List<Employee> items = new ArrayList<>();
    private final EventListenerList listeners = new EventListenerList();

    public void seed() {
        Employee a = new Employee();
        a.setName("Ada Lovelace");
        a.setPassword("analytical");
        a.setBio("Mathematician; conceived the first algorithm intended for a machine.");
        a.setRole(Employee.Role.ADMIN);
        a.setLevel(10);
        a.setRating(95);
        a.setFavorite(true);
        a.setDateOfBirth(date(1815, 12, 10));
        items.add(a);

        Employee b = new Employee();
        b.setName("Alan Turing");
        b.setPassword("enigma");
        b.setBio("Theoretical computer scientist; foundational work on computability.");
        b.setRole(Employee.Role.USER);
        b.setLevel(8);
        b.setRating(90);
        b.setDateOfBirth(date(1912, 6, 23));
        items.add(b);

        Employee c = new Employee();
        c.setName("Grace Hopper");
        c.setPassword("compiler");
        c.setBio("Compiler pioneer; coined \"debugging\".");
        c.setRole(Employee.Role.ADMIN);
        c.setLevel(9);
        c.setRating(92);
        c.setFavorite(true);
        c.setDateOfBirth(date(1906, 12, 9));
        items.add(c);

        Employee d = new Employee();
        d.setName("Guest");
        d.setPassword("");
        d.setBio("Sample inactive guest user for filter testing.");
        d.setActive(false);
        d.setRole(Employee.Role.GUEST);
        d.setLevel(1);
        d.setRating(10);
        d.setDateOfBirth(date(2000, 1, 1));
        items.add(d);
    }

    private static Date date(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month - 1, day);
        return cal.getTime();
    }

    public Employee get(int index) { return items.get(index); }

    public int size() { return items.size(); }

    public void add(Employee e) {
        items.add(e);
        fireChanged();
    }

    public void update(int index, Employee e) {
        items.set(index, e);
        fireChanged();
    }

    public void remove(int index) {
        items.remove(index);
        fireChanged();
    }

    public void addListener(Listener l) {
        listeners.add(Listener.class, l);
    }

    private void fireChanged() {
        for (Listener l : listeners.getListeners(Listener.class)) {
            l.itemsChanged();
        }
    }
}
