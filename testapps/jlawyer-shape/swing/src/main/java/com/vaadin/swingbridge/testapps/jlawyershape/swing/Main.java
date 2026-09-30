package com.vaadin.swingbridge.testapps.jlawyershape.swing;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.EventQueue;
import java.io.PrintWriter;
import java.io.StringWriter;

public final class Main {

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(Main::showError);
        SwingUtilities.invokeLater(() -> {
            CaseStore store = new CaseStore();
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
