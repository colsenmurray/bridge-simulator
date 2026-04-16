package bridge;

import bridge.ui.MainFrame;

/**
 * Application entry point.
 */
public class Main {

    public static void main(String[] args) {
        MainFrame.setLookAndFeel();
        new MainFrame();
    }

}
