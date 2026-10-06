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

import com.github.mvysny.kaributesting.v10.GridKt;
import com.github.mvysny.kaributesting.v10.LocatorJ;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.swingbridge.surrogates.SJCheckBox;
import com.vaadin.swingbridge.surrogates.SJDialog;
import com.vaadin.swingbridge.surrogates.SJLabel;
import com.vaadin.swingbridge.surrogates.SJTable;
import com.vaadin.swingbridge.surrogates.SScrollPane;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import vaadinx.awt.BorderLayout;
import vaadinx.awt.Button;
import vaadinx.awt.Checkbox;
import vaadinx.awt.CheckboxGroup;
import vaadinx.awt.Choice;
import vaadinx.awt.Component;
import vaadinx.awt.Dialog;
import vaadinx.awt.Frame;
import vaadinx.awt.Label;
import vaadinx.awt.Panel;
import vaadinx.awt.ScrollPane;
import vaadinx.awt.Scrollbar;
import vaadinx.awt.Window;
import vaadinx.swing.Box;
import vaadinx.swing.ButtonGroup;
import vaadinx.swing.ImageIcon;
import vaadinx.swing.JButton;
import vaadinx.swing.JCheckBox;
import vaadinx.swing.JCheckBoxMenuItem;
import vaadinx.swing.JColorChooser;
import vaadinx.swing.JComboBox;
import vaadinx.swing.JComponent;
import vaadinx.swing.JDesktopPane;
import vaadinx.swing.JDialog;
import vaadinx.swing.JEditorPane;
import vaadinx.swing.JFileChooser;
import vaadinx.swing.JFormattedTextField;
import vaadinx.swing.JFrame;
import vaadinx.swing.JInternalFrame;
import vaadinx.swing.JLabel;
import vaadinx.swing.JLayeredPane;
import vaadinx.swing.JList;
import vaadinx.swing.JMenu;
import vaadinx.swing.JMenuBar;
import vaadinx.swing.JMenuItem;
import vaadinx.swing.JOptionPane;
import vaadinx.swing.JPanel;
import vaadinx.swing.JPasswordField;
import vaadinx.swing.JPopupMenu;
import vaadinx.swing.JProgressBar;
import vaadinx.swing.JRadioButton;
import vaadinx.swing.JRadioButtonMenuItem;
import vaadinx.swing.JRootPane;
import vaadinx.swing.JScrollBar;
import vaadinx.swing.JScrollPane;
import vaadinx.swing.JSeparator;
import vaadinx.swing.JSlider;
import vaadinx.swing.JSpinner;
import vaadinx.swing.JSplitPane;
import vaadinx.swing.JTabbedPane;
import vaadinx.swing.JTable;
import vaadinx.swing.JTextArea;
import vaadinx.swing.JTextField;
import vaadinx.swing.JTextPane;
import vaadinx.swing.JToggleButton;
import vaadinx.swing.JToolBar;
import vaadinx.swing.JTree;
import vaadinx.swing.JViewport;
import vaadinx.swing.JWindow;
import vaadinx.swing.table.JTableHeader;

import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.Color;
import java.awt.event.ItemEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * A lazy emulator builds its Vaadin peer only once a UI is current: an emulator constructed and
 * configured on a worker runs no Vaadin code there, and its writes reach the peer when it is
 * attached (D_lazy_peers).
 */
class LazyPeerTest extends AbstractKaribuTest {

    /** Runs {@code body} on a thread with no UI and no context, off the Karibu lock. */
    private static void onBareThread(Runnable body) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                body.run();
            } catch (Throwable e) {
                failure.set(e);
            }
        }, "lazy-peer");
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
        if (t.isAlive()) fail("the thread did not finish");
        if (failure.get() != null) throw new AssertionError("the thread failed", failure.get());
    }

    @Test
    @DisplayName("a JLabel configured on a worker builds no Vaadin component there, and renders once attached")
    void jLabelBuiltOnWorker() {
        int builtOffThreadBefore = EHelper.peersBuiltOffUIThread();
        AtomicReference<JLabel> built = new AtomicReference<>();

        onBareThread(() -> {
            JLabel label = new JLabel("Name", new ImageIcon(new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB)),
                    SwingConstants.RIGHT);
            label.setForeground(Color.RED);
            label.setToolTipText("tip");
            label.setText("Full name");
            built.set(label);
        });

        assertEquals(builtOffThreadBefore, EHelper.peersBuiltOffUIThread(),
                "no peer was reached on the worker, so none was built there");
        JLabel label = built.get();
        assertEquals("Full name", label.getText(), "the emulator answers from its own state meanwhile");

        UI.getCurrent().add(label.getPeer());
        SJLabel peer = assertInstanceOf(SJLabel.class, label.getPeer());
        assertEquals("Full name", peer.getText(), "the queued writes reached the peer, in order");
        assertInstanceOf(Image.class, peer.getIcon(), "and the icon became an Image, with a UI current");
    }

    /** Every lazy emulator, constructed and configured the way a worker typically leaves one. */
    private static Map<String, Supplier<? extends Component>> lazyEmulators() {
        Map<String, Supplier<? extends Component>> m = new LinkedHashMap<>();
        m.put("JLabel", () -> new JLabel("x"));
        m.put("JPanel", JPanel::new);
        m.put("JPanel(BorderLayout)", () -> {
            JPanel p = new JPanel(new BorderLayout());
            p.add(new JLabel("north"), BorderLayout.NORTH);
            return p;
        });
        m.put("JSeparator", JSeparator::new);
        m.put("JScrollBar", JScrollBar::new);
        m.put("JViewport", JViewport::new);
        m.put("JLayeredPane", JLayeredPane::new);
        m.put("JTableHeader", JTableHeader::new);
        m.put("Box", () -> {
            Box box = Box.createHorizontalBox();
            box.add(Box.createHorizontalStrut(5));
            box.add(new JLabel("after the strut"));
            return box;
        });
        m.put("AWT Label", () -> new Label("x", Label.RIGHT));
        m.put("AWT Button", () -> {
            Button b = new Button("go");
            b.setActionCommand("cmd");
            return b;
        });
        m.put("AWT Checkbox", () -> new Checkbox("check", true, new CheckboxGroup()));
        m.put("AWT Choice", () -> {
            Choice c = new Choice();
            c.add("one");
            c.add("two");
            c.select(1);
            return c;
        });
        m.put("AWT List", () -> {
            vaadinx.awt.List l = new vaadinx.awt.List(3, true);
            l.add("one");
            l.add("two");
            l.select(0);
            return l;
        });
        m.put("AWT Scrollbar", () -> {
            Scrollbar s = new Scrollbar(Scrollbar.HORIZONTAL, 10, 5, 0, 100);
            s.setValue(20);
            return s;
        });
        m.put("AWT Panel", () -> {
            Panel p = new Panel();
            p.add(new Label("inside"));
            return p;
        });
        m.put("JButton", () -> {
            JButton b = new JButton("go");
            b.setActionCommand("cmd");
            b.setMnemonic('G');
            b.doClick();
            return b;
        });
        m.put("JToggleButton", () -> new JToggleButton("t", true));
        m.put("JCheckBox", () -> {
            JCheckBox c = new JCheckBox("c");
            c.doClick();
            return c;
        });
        m.put("JRadioButton", () -> {
            ButtonGroup g = new ButtonGroup();
            JRadioButton r = new JRadioButton("r", true);
            g.add(r);
            g.add(new JRadioButton("other"));
            return r;
        });
        m.put("JMenuItem", () -> new JMenuItem("item"));
        m.put("JCheckBoxMenuItem", () -> new JCheckBoxMenuItem("check", true));
        m.put("JRadioButtonMenuItem", () -> new JRadioButtonMenuItem("radio", true));
        m.put("JSlider", () -> {
            JSlider sl = new JSlider(JSlider.VERTICAL, 0, 10, 3);
            sl.setMajorTickSpacing(5);
            sl.setValue(7);
            return sl;
        });
        m.put("JProgressBar", () -> {
            JProgressBar pb = new JProgressBar(0, 10);
            pb.setValue(4);
            pb.setStringPainted(true);
            return pb;
        });
        m.put("JSpinner", () -> {
            JSpinner sp = new JSpinner(new SpinnerNumberModel(1, 0, 9, 1));
            sp.setValue(2);
            return sp;
        });
        m.put("JComboBox", () -> {
            JComboBox<String> cb = new JComboBox<>(new String[] {"a", "b"});
            cb.setSelectedIndex(1);
            cb.setMaximumRowCount(4);
            return cb;
        });
        m.put("JList", () -> {
            JList<String> l = new JList<>(new String[] {"a", "b", "c"});
            l.setSelectedIndex(2);
            return l;
        });
        m.put("JTree", () -> {
            DefaultMutableTreeNode root = new DefaultMutableTreeNode("root");
            root.add(new DefaultMutableTreeNode("child"));
            JTree t = new JTree(root);
            t.setSelectionRow(1);
            t.setRootVisible(false);
            return t;
        });
        m.put("JTextField", () -> {
            JTextField tf = new JTextField("abc", 10);
            tf.setEditable(false);
            tf.setCaretPosition(1);
            return tf;
        });
        m.put("JPasswordField", () -> new JPasswordField("secret", 8));
        m.put("JTextArea", () -> {
            JTextArea ta = new JTextArea("one\ntwo", 3, 20);
            ta.append("\nthree");
            return ta;
        });
        m.put("JFormattedTextField", () -> {
            JFormattedTextField f = new JFormattedTextField("text");
            f.setValue("other");
            return f;
        });
        m.put("JFormattedTextField(Integer)", () -> new JFormattedTextField(42));
        m.put("JEditorPane", () -> new JEditorPane("text/html", "<b>bold</b>"));
        m.put("JTextPane", () -> {
            JTextPane tp = new JTextPane();
            tp.setText("styled");
            return tp;
        });
        m.put("JScrollPane", () -> new JScrollPane(new JLabel("view"),
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER));
        m.put("JSplitPane", () -> {
            JSplitPane sp = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                    new JLabel("top"), new JLabel("bottom"));
            sp.setDividerLocation(40);
            return sp;
        });
        m.put("JToolBar", () -> {
            JToolBar tb = new JToolBar("tools", JToolBar.VERTICAL);
            tb.add(new JButton("go"));
            tb.addSeparator();
            return tb;
        });
        m.put("JTabbedPane", () -> {
            JTabbedPane tp = new JTabbedPane();
            tp.addTab("one", new JLabel("1"));
            tp.addTab("two", new JLabel("2"));
            tp.setSelectedIndex(1);
            return tp;
        });
        m.put("JColorChooser", () -> new JColorChooser(Color.RED));
        m.put("JMenuBar", () -> {
            JMenuBar bar = new JMenuBar();
            JMenu menu = new JMenu("File");
            menu.add(new JMenuItem("Open"));
            menu.add(new JCheckBoxMenuItem("Wrap", true));
            bar.add(menu);
            return bar;
        });
        m.put("JPopupMenu", () -> {
            JPopupMenu popup = new JPopupMenu();
            popup.add(new JMenuItem("Cut"));
            popup.setInvoker(new JLabel("target"));
            return popup;
        });
        m.put("JDesktopPane", JDesktopPane::new);
        m.put("JOptionPane", () -> new JOptionPane("message"));
        m.put("JFileChooser", JFileChooser::new);
        m.put("JRootPane", JRootPane::new);
        m.put("JTable", () -> {
            JTable t = new JTable(new Object[][] {{"a", 1}, {"b", 2}}, new Object[] {"name", "n"});
            t.setRowSelectionInterval(1, 1);
            t.setAutoCreateRowSorter(true);
            return t;
        });
        m.put("AWT Window", () -> {
            Window w = new Window((Frame) null);
            w.setBounds(10, 20, 300, 200);
            return w;
        });
        m.put("AWT Frame", () -> {
            Frame f = new Frame("title");
            f.setResizable(false);
            f.setUndecorated(true);
            return f;
        });
        m.put("AWT Dialog", () -> {
            Dialog d = new Dialog(new Frame("owner"), "title", true);
            d.setResizable(false);
            d.setModal(false);
            return d;
        });
        m.put("JWindow", () -> {
            JWindow w = new JWindow(new JFrame("owner"));
            w.add(new JLabel("inside"));
            return w;
        });
        m.put("JFrame", () -> {
            JFrame f = new JFrame("title");
            f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            f.setJMenuBar(new JMenuBar());
            f.add(new JLabel("inside"), BorderLayout.CENTER);
            f.setLocation(5, 5);
            return f;
        });
        m.put("JDialog", () -> {
            JDialog d = new JDialog(new JFrame("owner"), "title", true);
            JButton ok = new JButton("OK");
            d.add(ok);
            d.getRootPane().setDefaultButton(ok);
            d.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
            return d;
        });
        m.put("JInternalFrame", () -> {
            JInternalFrame f = new JInternalFrame("title", true, true, true, true);
            f.add(new JLabel("inside"));
            f.setSize(200, 100);
            f.setTitle("other");
            f.getRootPane();
            return f;
        });
        m.put("AWT ScrollPane", () -> {
            ScrollPane sp = new ScrollPane(ScrollPane.SCROLLBARS_NEVER);
            sp.add(new Label("first"));
            sp.setScrollPosition(0, 40);
            sp.add(new Label("replaces the first"));
            return sp;
        });
        return m;
    }

    @TestFactory
    @DisplayName("a lazy emulator configured on a worker builds no Vaadin component there")
    Stream<DynamicTest> builtOnWorker() {
        return lazyEmulators().entrySet().stream().map(e -> DynamicTest.dynamicTest(
                e.getKey(), () -> assertBuiltOnWorkerWithoutVaadin(e.getKey(), e.getValue())));
    }

    private static void assertBuiltOnWorkerWithoutVaadin(String name,
            Supplier<? extends Component> factory) {
        int builtOffThreadBefore = EHelper.peersBuiltOffUIThread();
        AtomicReference<Component> built = new AtomicReference<>();

        onBareThread(() -> {
            Component c = factory.get();
            c.setName("named");
            c.setEnabled(false);
            c.setBackground(Color.YELLOW);
            if (c instanceof JComponent jc) jc.setToolTipText("tip");
            built.set(c);
        });

        assertEquals(builtOffThreadBefore, EHelper.peersBuiltOffUIThread(),
                name + " reached its peer on the worker; the WARN's stack names the reach");
        UI.getCurrent().add(built.get().getPeer());
        assertEquals(true, built.get().getPeer().isAttached(), "and it renders once attached");
    }

    @Test
    @DisplayName("a JDialog built and filled on a worker shows its title, content and shared root pane once shown")
    void jDialogBuiltOnWorker() {
        int builtOffThreadBefore = EHelper.peersBuiltOffUIThread();
        AtomicReference<JDialog> built = new AtomicReference<>();
        AtomicReference<JLabel> label = new AtomicReference<>();
        onBareThread(() -> {
            JDialog d = new JDialog((Frame) null, "first", false);
            label.set(new JLabel("content"));
            d.add(label.get());
            d.setTitle("second");
            d.setResizable(false);
            built.set(d);
        });
        assertEquals(builtOffThreadBefore, EHelper.peersBuiltOffUIThread(),
                "no window peer was built on the worker");

        JDialog dialog = built.get();
        dialog.setVisible(true);
        SJDialog peer =
                assertInstanceOf(SJDialog.class, dialog.getPeer());
        assertEquals(true, peer.isOpened(), "the show attached and opened the overlay");
        assertEquals("second", peer.getTitle(), "the queued title writes drained in order");
        assertEquals(false, peer.isResizable());
        assertEquals(dialog.getRootPane().getPeer(), peer.getRootPane(),
                "the surrogate holds the emulator's root pane, not a second one");
        assertEquals(true, label.get().getPeer().isAttached(), "and the content rendered inside it");
    }

    @Test
    @DisplayName("a JDialog's invalid close operation throws at the call, before any peer exists")
    void jDialogCloseOperationThrowsEagerly() {
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        onBareThread(() -> {
            JDialog d = new JDialog();
            try {
                d.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            } catch (IllegalArgumentException e) {
                thrown.set(e);
            }
        });
        assertInstanceOf(IllegalArgumentException.class, thrown.get());
    }

    @Test
    @DisplayName("a JCheckBox selected on a worker shows checked once attached, and a click reaches its listeners")
    void jCheckBoxSelectedOnWorker() {
        AtomicReference<JCheckBox> built = new AtomicReference<>();
        onBareThread(() -> {
            JCheckBox c = new JCheckBox("c");
            c.setSelected(true);
            built.set(c);
        });

        JCheckBox box = built.get();
        java.util.List<String> events = new ArrayList<>();
        box.addItemListener(e -> events.add("item " + e.getStateChange()));
        box.addActionListener(e -> events.add("action " + e.getActionCommand()));
        UI.getCurrent().add(box.getPeer());
        SJCheckBox peer =
                assertInstanceOf(SJCheckBox.class, box.getPeer());
        assertEquals(true, peer.getValue(), "the surrogate renders the emulator's model");

        LocatorJ._setValue(peer, false);
        assertEquals(false, box.isSelected());
        assertEquals(java.util.List.of("item " + ItemEvent.DESELECTED, "action c"), events);
    }

    @Test
    @DisplayName("a JFormattedTextField given a value on a worker shows its text once attached")
    void jFormattedTextFieldValueOnWorker() {
        AtomicReference<JFormattedTextField> built = new AtomicReference<>();
        java.util.List<String> documentEvents = new CopyOnWriteArrayList<>();
        onBareThread(() -> {
            JFormattedTextField f = new JFormattedTextField("first");
            f.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { documentEvents.add(Thread.currentThread().getName()); }
                public void removeUpdate(DocumentEvent e) { documentEvents.add(Thread.currentThread().getName()); }
                public void changedUpdate(DocumentEvent e) { }
            });
            f.setValue("second");
            built.set(f);
        });

        assertEquals("second", built.get().getText(), "the Document took the text on the worker, as the JDK's formatter sets it");
        assertEquals(java.util.List.of("lazy-peer", "lazy-peer"), documentEvents, "remove then insert, both on the worker");
        UI.getCurrent().add(built.get().getPeer());
        TextField peer =
                assertInstanceOf(TextField.class, built.get().getPeer());
        assertEquals("second", peer.getValue());
        assertEquals(2, documentEvents.size(), "the drained writes did not echo into the Document");
    }

    @Test
    @DisplayName("an AWT ScrollPane whose child was replaced on a worker shows the second child once attached")
    void scrollPaneChildSwapOnWorker() {
        AtomicReference<ScrollPane> built = new AtomicReference<>();
        AtomicReference<Label> second = new AtomicReference<>();

        onBareThread(() -> {
            ScrollPane sp = new ScrollPane();
            sp.add(new Label("first"));
            second.set(new Label("second"));
            sp.add(second.get());
            built.set(sp);
        });

        UI.getCurrent().add(built.get().getPeer());
        SScrollPane peer =
                assertInstanceOf(SScrollPane.class, built.get().getPeer());
        assertEquals(second.get().getPeer(), peer.getContent(), "the queued content swaps drained in order");
    }

    @Test
    @DisplayName("a JTable built and filled on a worker shows its columns and rows once attached")
    void jTableBuiltOnWorker() {
        AtomicReference<JTable> built = new AtomicReference<>();
        onBareThread(() -> {
            DefaultTableModel model = new DefaultTableModel(
                    new Object[][] {{"a", 1}}, new Object[] {"name", "n"});
            JTable t = new JTable(model);
            model.addRow(new Object[] {"b", 2});
            t.getColumnModel().getColumn(0).setHeaderValue("Name");
            built.set(t);
        });

        JTable table = built.get();
        assertEquals(2, table.getColumnCount(), "the table created its own columns on the worker");
        assertEquals(2, table.getRowCount());
        UI.getCurrent().add(table.getPeer());
        SJTable peer =
                assertInstanceOf(SJTable.class, table.getPeer());
        assertEquals(2, peer.getColumnModel().getColumnCount(), "the queued column writes reached the surrogate, once each");
        assertEquals(2, GridKt._size(peer));
    }

    @Test
    @DisplayName("a label added to a panel on a worker is built when the panel's add runs with a UI")
    void jLabelInPanelBuiltOnWorker() {
        AtomicReference<JPanel> built = new AtomicReference<>();

        onBareThread(() -> {
            JPanel panel = new JPanel();
            panel.add(new JLabel("inside"));
            built.set(panel);
        });

        UI.getCurrent().add(built.get().getPeer());
        JLabel label = (JLabel) built.get().getComponent(0);
        SJLabel peer = assertInstanceOf(SJLabel.class, label.getPeer());
        assertEquals(built.get().getPeer(), peer.getParent().orElseThrow(), "the add put the label's peer in the panel's");
        assertEquals("inside", peer.getText());
    }
}
