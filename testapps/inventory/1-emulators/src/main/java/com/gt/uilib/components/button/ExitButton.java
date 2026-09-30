package com.gt.uilib.components.button;

import com.gt.common.ResourceManager;
import com.gt.uilib.components.AbstractFunctionPanel;
import com.gt.uilib.components.AppFrame;
import org.apache.log4j.Logger;
import vaadinx.swing.border.EtchedBorder;
import vaadinx.awt.event.MouseAdapter;
import vaadinx.awt.event.MouseEvent;
import vaadinx.awt.event.MouseListener;
import vaadinx.swing.ImageIcon;
import vaadinx.swing.JOptionPane;

public class ExitButton extends ActionButton {

    static final Logger logger = Logger.getLogger(ExitButton.class);

    public ExitButton(String Text, ImageIcon on, ImageIcon off, String panelQualifiedClassName) {
        super(Text, on, off, panelQualifiedClassName);
    }

    public static ExitButton create(String text, String fileName, String panelQualifiedClassName) {
        String onFile = fileName + "-on.png";
        String offFile = fileName + "-off.png";
        return new ExitButton(text, ResourceManager.getImageIcon(onFile), ResourceManager.getImageIcon(offFile), panelQualifiedClassName);
    }

    public static void handleExit() {
        int res = 0;
        if (AppFrame.getCurrentWindow() != null) {
            if (!AppFrame.getCurrentWindow().isReadyToClose)
                res = JOptionPane.showConfirmDialog(AppFrame.getInstance(), AbstractFunctionPanel.getUnsavedExitMessage(), "Exit Confirmation",
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        } else {
            res = JOptionPane.showConfirmDialog(AppFrame.getInstance(), "Are you sure to exit", "Exit Confirmation", JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
        }

        if (res == JOptionPane.YES_OPTION) {
            // setVisible(false);
            logger.info("Shutting Down");
            // disposing the last window ends this user's session, as System.exit ended the desktop JVM
            AppFrame.getInstance().dispose();
            // TODO: DB connection close
        }
    }

    @Override
    protected final void initListner() {
        addMouseListener(getExitMouseListener());
    }

    protected final MouseListener getExitMouseListener() {
        MouseListener ml = new MouseAdapter() {

            public void mouseReleased(MouseEvent e) {
                handleExit();
            }

            public void mouseEntered(MouseEvent e) {
                setBorder(new EtchedBorder(EtchedBorder.LOWERED));
                highlight();
            }

            public void mouseExited(MouseEvent e) {
                setBorder(null);
                unhighlight();
            }

            public void mouseClicked(MouseEvent e) {
                setBorder(null);
                unhighlight();
            }
        };
        return ml;
    }
}
