package com.ca.ui;

import com.ca.db.model.ApplicationLog;
import com.ca.db.model.LoginUser;
import com.ca.db.service.DBUtils;
import com.ca.db.service.LoginUserServiceImpl;
import com.gt.common.AppStarter;
import com.gt.uilib.components.AppFrame;
import org.apache.commons.lang3.SystemUtils;
import com.vaadin.swingbridge.fixture.Seed;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.util.Date;

public class Main {

    private static AppFrame gui;

    private static void setUpAndShowGui() {
        gui = AppFrame.getInstance();
        gui.setVisible(true);
        gui.setLocationRelativeTo(null); // center the component onscreen
        gui.addComponentListener(new java.awt.event.ComponentAdapter() {

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

    public static void showMaximized() {
        gui.setState(java.awt.Frame.NORMAL);
    }

    /**
     * Launch the application.
     */
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

        startApp();

        addUserForFirstTime();
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

    private static void startApp() {

        EventQueue.invokeLater(() -> {

            if (new AppStarter().alreadyRunning) {
                setApplicationStartLog();
                setUpAndShowGui();
            } else {
                System.exit(0);
            }
        });
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


}
