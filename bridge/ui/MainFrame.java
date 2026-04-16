package bridge.ui;

import java.awt.Dimension;
import java.awt.DisplayMode;
import java.awt.Font;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;

import com.formdev.flatlaf.FlatLightLaf;

/**
 * Main application window.
 */
public class MainFrame extends JFrame {

    private GamePanel gamePanel;
    private LevelEditor levelEditor;
    private final int MIN_WIDTH = 960;
    private final int MIN_HEIGHT = 540;

    public MainFrame() {
        setTitle("Bridges");
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        setExtendedState(MAXIMIZED_BOTH);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        int refreshRate = getRefreshRate();
        Box2D box2d = new Box2D(this);

        gamePanel = new GamePanel(this, box2d, refreshRate);
        levelEditor = new LevelEditor(this, box2d, refreshRate);
        showGame();
        setVisible(true);
        gamePanel.showWelcomeMessage();
    }

    public void showGame() {
        swapScreen(levelEditor, gamePanel);
        gamePanel.refreshLevelList();
        gamePanel.checkLevelsPresent();
    }

    public void showLevelEditor() {
        swapScreen(gamePanel, levelEditor);
    }

    private void swapScreen(JPanel current, JPanel next) {
        remove(current);
        add(next);
        next.repaint();
        setVisible(true);
    }

    private int getRefreshRate() {
        try {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice[] gs = ge.getScreenDevices();
            DisplayMode dm = gs[0].getDisplayMode();
            return dm.getRefreshRate();
        } catch (Exception e) {
            e.printStackTrace();
            return 60;
        }
    }

    public static void setLookAndFeel() {
        FlatLightLaf.setup();

        int arc = 20;
        UIManager.put("Button.arc", arc);
        UIManager.put("Component.arc", arc);
        UIManager.put("TextComponent.arc", arc);

        int margin = 8;
        Insets insets = new Insets(margin, margin, margin, margin);
        UIManager.put("Button.margin", insets);
        UIManager.put("TextField.margin", insets);
        UIManager.put("TextComponent.margin", insets);
        UIManager.put("ComboBox.padding", insets);

        Font defaultFont = UIManager.getDefaults().getFont("Label.font");
        Font font = defaultFont.deriveFont(18f);
        Font fontBold = font.deriveFont(Font.BOLD);
        FontUIResource fontResource = new FontUIResource(font);
        FontUIResource fontResourceBold = new FontUIResource(fontBold);
        UIManager.put("Label.font", fontResourceBold);
        UIManager.put("Button.font", fontResource);
        UIManager.put("ComboBox.font", fontResource);
        UIManager.put("TextField.font", fontResource);
    }

}
