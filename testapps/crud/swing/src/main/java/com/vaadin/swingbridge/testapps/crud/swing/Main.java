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

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.EventQueue;
import java.io.PrintWriter;
import java.io.StringWriter;

public final class Main {

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(Main::showError);
        SwingUtilities.invokeLater(() -> {
            EmployeeStore store = new EmployeeStore();
            store.seed();
            new MainFrame(store).setVisible(true);
        });
    }

    private static void showError(Thread t, Throwable e) {
        e.printStackTrace();
        Runnable show = () -> {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            JOptionPane.showMessageDialog(null, sw.toString(),
                    e.getClass().getSimpleName() + ": " + e.getMessage(),
                    JOptionPane.ERROR_MESSAGE);
        };
        if (EventQueue.isDispatchThread()) show.run();
        else SwingUtilities.invokeLater(show);
    }
}
