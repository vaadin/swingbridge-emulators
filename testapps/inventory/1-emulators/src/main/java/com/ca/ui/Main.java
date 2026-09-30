package com.ca.ui;

import com.ca.db.model.ApplicationLog;
import com.ca.db.model.LoginUser;
import com.ca.db.service.DBUtils;
import com.ca.db.service.LoginUserServiceImpl;
import com.github.mvysny.vaadinboot.VaadinBoot;
import com.gt.uilib.components.AppFrame;
import org.apache.commons.lang3.SystemUtils;
import com.vaadin.swingbridge.fixture.Seed;

import java.awt.Dimension;
import vaadinx.awt.event.ComponentEvent;
import vaadinx.swing.SwingUtilities;
import vaadinx.swing.UIManager;
import java.io.File;
import java.util.Date;

/**
 * The two entry points of the migrated app: {@link #main} once per JVM, {@link #mainUI} once per
 * browser tab (from {@link AppRoute}).
 */
public final class Main {

    /** Once per JVM. Everything here runs before the first request — {@code run()} blocks until shutdown. */
    public static void main(String[] args) throws Exception {
        if (SystemUtils.IS_OS_WINDOWS) {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        }

        File f = new File("log");
        f.mkdir();

        // SB-Emulators addition, not upstream's: the app ships no seed data at all, and upstream expects the
        // reference tables to be typed in by hand before Item Entry works. Seeding here means every
        // migration stage opens on identical rows without needing a launcher of its own. See
        // ../PROVENANCE.md.
        Seed.seedIfEmpty();

        addUserForFirstTime();

        new VaadinBoot()
                .useVirtualThreadsIfAvailable(false)  // not optional: the blocking-dialog executor runs its
                .run();                               // own virtual threads on platform request threads
    }

    /** Once per browser tab, from {@link AppRoute}. */
    public static void mainUI() {
        SwingUtilities.invokeLater(() -> {
            setApplicationStartLog();
            setUpAndShowGui();
        });
    }

    private static void setUpAndShowGui() {
        final AppFrame gui = new AppFrame();
        gui.setVisible(true);
        gui.setLocationRelativeTo(null); // center the component onscreen
        gui.addComponentListener(new vaadinx.awt.event.ComponentAdapter() {

            @Override
            public void componentResized(ComponentEvent event) {
                Dimension dGUI = new Dimension(Math.max(780, gui.getWidth()), Math.max(580, gui.getHeight()));
                Dimension mindGUI = new Dimension(780, 580);
                gui.setMinimumSize(mindGUI);
                gui.setPreferredSize(mindGUI);
                gui.setSize(dGUI);
            }
        });
    }

    private static void addUserForFirstTime() throws Exception {
        LoginUserServiceImpl lus = new LoginUserServiceImpl();
        if (!LoginUserServiceImpl.userExists()) {
            LoginUser lu = new LoginUser();
            lu.setdFlag(1);
            lu.setUsername("ADMIN");
            lu.setPassword("ADMIN");
            lus.saveLoginUser(lu);
        }
    }

    private static void setApplicationStartLog() {
        try {
            ApplicationLog log = new ApplicationLog();
            log.setDateTime(new Date());
            log.setMessage("Application Started");
            DBUtils.saveOrUpdate(log);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Main() {
    }
}
