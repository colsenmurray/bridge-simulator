package bridge;

import bridge.ui.MainFrame;

/**
 * Application entry point.
 */
public class Main {

    public static void main(String[] args) {
        if (args.length > 0 && "--headless".equals(args[0])) {
            System.exit(HeadlessSimulation.run(args));
        }
        MainFrame.setLookAndFeel();
        new MainFrame();
    }

}
