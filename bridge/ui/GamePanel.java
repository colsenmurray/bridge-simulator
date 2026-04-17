package bridge.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.event.MouseInputListener;

import org.jbox2d.common.Vec2;

import bridge.level.Level;
import bridge.physics.GameSession;
import bridge.physics.beams.Material;
import bridge.save.BridgeJson;
import bridge.save.BridgeSaveFile;
import bridge.save.LevelFingerprint;

/**
 * Main game / simulation panel.
 */
public class GamePanel extends JPanel implements ActionListener, MouseInputListener {

    private static final Path BRIDGES_DIR = Paths.get("res", "bridges");

    private MainFrame mainFrame;
    private Box2D box2d;
    private GameSession session;

    private Map<String, Integer> bestPrices;

    private int mouseButton;
    private boolean mouseClicked = false;
    private Vec2 mousePos = new Vec2();

    private static final int PHYSICS_TICK_MS = 16;
    private long lastPhysicsTime;
    private Timer renderTimer;
    private Timer physicsTimer;

    private JButton prevLevelButton;
    private JComboBox<String> levelComboBox;
    private JButton nextLevelButton;
    private JButton asphaltButton;
    private JButton steelButton;
    private JButton woodButton;
    private JButton runPauseButton;
    private JButton restartButton;
    private JButton editorButton;
    private JButton saveBridgeButton;
    private JButton loadBridgeButton;
    private JLabel priceLabel;
    private JLabel budgetLabel;
    private JLabel bestLabel;

    public GamePanel(MainFrame mainFrame, Box2D box2d, int refreshRate) {
        this.mainFrame = mainFrame;
        this.box2d = box2d;
        bestPrices = new HashMap<String, Integer>();
        buildUi();

        refreshLevelList();

        physicsTimer = new Timer(PHYSICS_TICK_MS, this);
        physicsTimer.start();

        int fps = (int) (1.0 / refreshRate * 1000.0);
        renderTimer = new Timer(fps, this);
        renderTimer.start();

        addMouseListener(this);
        addMouseMotionListener(this);
    }

    private void buildUi() {
        this.setLayout(new BorderLayout());
        this.setOpaque(false);

        JPanel topRow = new RowPanel(box2d.getPixelWidth() / 20, box2d.getPixelHeight() / 100);
        this.add(topRow, BorderLayout.PAGE_START);

        JPanel levelColumn = new ColumnPanel();
        topRow.add(levelColumn);
        JLabel levelHeader = new JLabel("Level");
        levelColumn.add(levelHeader);
        JPanel levelRow = new RowPanel();
        levelColumn.add(levelRow);
        prevLevelButton = new JButton("Previous");
        prevLevelButton.addActionListener(this);
        levelRow.add(prevLevelButton);
        levelComboBox = new JComboBox<String>();
        levelComboBox.addActionListener(this);
        levelRow.add(levelComboBox);
        nextLevelButton = new JButton("Next");
        nextLevelButton.addActionListener(this);
        levelRow.add(nextLevelButton);

        JPanel materialColumn = new ColumnPanel();
        topRow.add(materialColumn);
        JLabel materialsHeader = new JLabel("Materials");
        materialColumn.add(materialsHeader);
        JPanel materialRow = new RowPanel();
        materialColumn.add(materialRow);
        asphaltButton = new JButton("Asphalt");
        asphaltButton.setToolTipText("The only material the car can drive on");
        asphaltButton.addActionListener(this);
        materialRow.add(asphaltButton);
        woodButton = new JButton("Wood");
        woodButton.setToolTipText("Cheap material but not very resistant");
        woodButton.addActionListener(this);
        materialRow.add(woodButton);
        steelButton = new JButton("Steel");
        steelButton.setToolTipText("More expensive material but also more solid");
        steelButton.addActionListener(this);
        materialRow.add(steelButton);

        JPanel simColumn = new ColumnPanel();
        topRow.add(simColumn);
        JLabel simHeader = new JLabel("Simulation");
        simColumn.add(simHeader);
        JPanel simRow = new RowPanel();
        simColumn.add(simRow);
        runPauseButton = new JButton();
        runPauseButton.addActionListener(this);
        simRow.add(runPauseButton);
        restartButton = new JButton("Restart");
        restartButton.addActionListener(this);
        simRow.add(restartButton);

        JPanel editorColumn = new ColumnPanel();
        topRow.add(editorColumn);
        JLabel editorHeader = new JLabel("Editor");
        editorColumn.add(editorHeader);
        JPanel editorRow = new RowPanel();
        editorColumn.add(editorRow);
        editorButton = new JButton("Edit a Level");
        editorButton.addActionListener(this);
        editorRow.add(editorButton);

        JPanel bridgeFileColumn = new ColumnPanel();
        topRow.add(bridgeFileColumn);
        JLabel bridgeFileHeader = new JLabel("Bridge file");
        bridgeFileColumn.add(bridgeFileHeader);
        JPanel bridgeFileRow = new RowPanel();
        bridgeFileColumn.add(bridgeFileRow);
        saveBridgeButton = new JButton("Save bridge");
        saveBridgeButton.setToolTipText("Save bridge under a name in res/bridges/ (pause simulation first)");
        saveBridgeButton.addActionListener(this);
        bridgeFileRow.add(saveBridgeButton);
        loadBridgeButton = new JButton("Load bridge");
        loadBridgeButton.setToolTipText("Pick a saved bridge from res/bridges/");
        loadBridgeButton.addActionListener(this);
        bridgeFileRow.add(loadBridgeButton);

        JPanel bottomRow = new RowPanel(box2d.getPixelWidth() / 20, box2d.getPixelHeight() / 50);
        this.add(bottomRow, BorderLayout.PAGE_END);

        JPanel priceColumn = new ColumnPanel();
        bottomRow.add(priceColumn);
        JLabel priceHeader = new JLabel("Price");
        priceColumn.add(priceHeader);
        priceLabel = new JLabel();
        priceColumn.add(priceLabel);

        JPanel budgetColumn = new ColumnPanel();
        bottomRow.add(budgetColumn);
        JLabel budgetHeader = new JLabel("Budget");
        budgetColumn.add(budgetHeader);
        budgetLabel = new JLabel();
        budgetColumn.add(budgetLabel);

        JPanel bestColumn = new ColumnPanel();
        bottomRow.add(bestColumn);
        JLabel bestHeader = new JLabel("Best");
        bestColumn.add(bestHeader);
        bestLabel = new JLabel();
        bestColumn.add(bestLabel);
    }

    @Override
    public void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Toolkit.getDefaultToolkit().sync();

        g.setColor(Color.decode("#55a3d4"));
        g.fillRect(0, 0, getWidth(), getHeight());

        if (session != null) {
            updatePriceLabels();
            session.draw(g, box2d, mousePos);
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();

        if (session != null) {
            if (source == physicsTimer) {
                long now = System.currentTimeMillis();
                float dt = (now - lastPhysicsTime) / 1000f;
                lastPhysicsTime = now;

                session.tickPhysics(mousePos, mouseButton, mouseClicked, dt);
                mouseClicked = false;
            }

            if (source == renderTimer) {
                repaint();
            }

            if (source == prevLevelButton) {
                int index = levelComboBox.getSelectedIndex();
                if (index > 0) {
                    index--;
                    levelComboBox.setSelectedIndex(index);
                }
            }

            if (source == nextLevelButton) {
                int index = levelComboBox.getSelectedIndex();
                if (index < levelComboBox.getItemCount() - 1) {
                    index++;
                    levelComboBox.setSelectedIndex(index);
                }
            }

            if (source == levelComboBox) {
                newSession();
            }

            Material material = null;
            if (source == woodButton) {
                material = Material.WOOD;
            }
            if (source == asphaltButton) {
                material = Material.ASPHALT;
            }
            if (source == steelButton) {
                material = Material.STEEL;
            }
            if (material != null) {
                session.changeMaterial(material);
            }

            if (source == runPauseButton) {
                session.toggleSimulationPhysics();
                updateSimulationButton();
            }

            if (source == restartButton) {
                newSession();
            }
        }

        if (source == editorButton) {
            if (session != null) {
                session.setSimulationPhysics(false);
                updateSimulationButton();
            }
            mainFrame.showLevelEditor();
        }

        if (source == saveBridgeButton) {
            saveBridgeToSidecar();
        }
        if (source == loadBridgeButton) {
            loadBridgeFromSidecar();
        }
    }

    /**
     * Sorted stems (no ".json") of files in {@code res/bridges}.
     */
    private String[] listSavedBridgeNames() {
        File dir = BRIDGES_DIR.toFile();
        if (!dir.isDirectory()) {
            return new String[0];
        }
        String[] files = dir.list((d, name) -> name.toLowerCase().endsWith(".json"));
        if (files == null || files.length == 0) {
            return new String[0];
        }
        String[] stems = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            String n = files[i];
            stems[i] = n.substring(0, n.length() - ".json".length());
        }
        Arrays.sort(stems);
        return stems;
    }

    private static String sanitizeBridgeFileName(String raw) {
        if (raw == null) {
            return "bridge";
        }
        String s = raw.trim();
        if (s.toLowerCase().endsWith(".json")) {
            s = s.substring(0, s.length() - ".json".length()).trim();
        }
        if (s.isEmpty()) {
            return "bridge";
        }
        s = s.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (".".equals(s) || "..".equals(s)) {
            return "bridge";
        }
        return s;
    }

    private Path pathForBridgeName(String stem) {
        return BRIDGES_DIR.resolve(sanitizeBridgeFileName(stem) + ".json");
    }

    private void saveBridgeToSidecar() {
        if (session == null) {
            return;
        }
        if (!session.canSaveOrLoadBridge()) {
            JOptionPane.showMessageDialog(mainFrame,
                    "Stop the simulation before saving the bridge (pause or stay in build mode).",
                    "Save bridge", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String level = getSelectedLevelName();
        String suggested = level != null && !level.isEmpty() ? sanitizeBridgeFileName(level) : "bridge";
        Object input = JOptionPane.showInputDialog(mainFrame,
                "Bridge file name (saved as res/bridges/<name>.json):",
                "Save bridge",
                JOptionPane.PLAIN_MESSAGE,
                null,
                null,
                suggested);
        if (input == null) {
            return;
        }
        Path path = pathForBridgeName(input.toString());
        try {
            BridgeSaveFile data = session.exportBridgeSave();
            BridgeJson.writeFile(path, data);
            JOptionPane.showMessageDialog(mainFrame, "Saved:\n" + path.toAbsolutePath(), "Save bridge",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(mainFrame, ex.getMessage(), "Save bridge", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void loadBridgeFromSidecar() {
        if (session == null) {
            return;
        }
        if (!session.canSaveOrLoadBridge()) {
            JOptionPane.showMessageDialog(mainFrame,
                    "Stop the simulation before loading a bridge.",
                    "Load bridge", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String[] names = listSavedBridgeNames();
        if (names.length == 0) {
            JOptionPane.showMessageDialog(mainFrame,
                    "No saved bridges found in:\n" + BRIDGES_DIR.toAbsolutePath(),
                    "Load bridge",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        Object choice = JOptionPane.showInputDialog(mainFrame,
                "Choose a saved bridge:",
                "Load bridge",
                JOptionPane.PLAIN_MESSAGE,
                null,
                names,
                names[0]);
        if (choice == null) {
            return;
        }
        Path path = pathForBridgeName(choice.toString());
        if (!Files.isRegularFile(path)) {
            JOptionPane.showMessageDialog(mainFrame, "File not found:\n" + path.toAbsolutePath(),
                    "Load bridge", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            BridgeSaveFile data = BridgeJson.readFile(path, session.getLevel());
            String expected = LevelFingerprint.compute(session.getLevel());
            if (data.getLevelFingerprint() != null && !data.getLevelFingerprint().equals(expected)) {
                int r = JOptionPane.showConfirmDialog(mainFrame,
                        "This file was saved for a different level geometry (fingerprint mismatch).\n"
                                + "Continue anyway?",
                        "Load bridge", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (r != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            session.applyBridgeSave(data);
            runPauseButton.setText("Start");
            repaint();
            JOptionPane.showMessageDialog(mainFrame, "Bridge loaded from:\n" + path.toAbsolutePath(),
                    "Load bridge", JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException | IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(mainFrame, ex.getMessage(), "Load bridge", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updatePriceLabels() {
        priceLabel.setText(Integer.toString(session.getTotalPrice()) + " $");
        budgetLabel.setText(Integer.toString(session.getBudget()) + " $");
        int best = getBestPrice();
        if (best == -1) {
            bestLabel.setText("Ø");
        } else {
            bestLabel.setText(Integer.toString(best) + " $");
        }
    }

    public void refreshLevelList() {
        String previous = getSelectedLevelName();
        String[] names = new File(Level.LEVELS_PATH.toString()).list();
        if (names == null) {
            names = new String[0];
        }
        Arrays.sort(names);
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<String>(names);
        levelComboBox.setModel(model);
        if (previous != null && Arrays.asList(names).contains(previous)) {
            levelComboBox.setSelectedItem(previous);
        } else {
            newSession();
        }
    }

    private void updateSimulationButton() {
        resetPhysicsClock();
        if (session.isPhysicsRunning()) {
            runPauseButton.setText("Pause");
        } else {
            runPauseButton.setText("Resume");
        }
    }

    private Level loadSelectedLevel() {
        runPauseButton.setText("Start");
        String name = getSelectedLevelName();
        return Level.load(mainFrame, name);
    }

    private String getSelectedLevelName() {
        return (String) levelComboBox.getSelectedItem();
    }

    public void onSessionEnd(boolean success, int price) {
        updateBestPrice(price);
        showEndMessage(success);
        resetPhysicsClock();
    }

    public void showWelcomeMessage() {
        String text = "Welcome to our bridge game!";
        text += "\n\n" + "You can build your bridge by clicking on connections (the circles).";
        text += "\n" + "Choose the material carefully based on its properties and price.";
        text += "\n" + "When you're ready, launch the simulation by clicking the ";
        text += runPauseButton.getText() + ".";
        text += "\n\n" + "The level will be successful if the car reaches the other side";
        text += "\n" + "and if the bridge price is less than the budget.";
        text += "\n\n" + "Good luck!";
        JOptionPane.showMessageDialog(mainFrame, text, "Tutorial", JOptionPane.PLAIN_MESSAGE);
    }

    private void showEndMessage(boolean success) {
        String text = "";
        String title = "";
        if (success) {
            title = "Level Completed";
            text += "Congratulations, you completed level " + getSelectedLevelName() + "!";
            text += "\n\n" + "Price: " + Integer.toString(session.getTotalPrice()) + " $";
            text += "\n" + "Best: " + Integer.toString(getBestPrice()) + " $";
            text += "\n\n" + "You can move to the next level";
            text += "\n" + "or try to build a cheaper bridge.";
        } else {
            title = "Level Failed";
            text += "Too bad, you failed level " + getSelectedLevelName() + ".";
            text += "\n\n" + "Your bridge cost too much:";
            text += "\n" + "Price: " + Integer.toString(session.getTotalPrice()) + " $";
            text += "\n" + "Budget: " + Integer.toString(session.getBudget()) + " $";
            text += "\n\n" + "You can try again by clicking " + restartButton.getText() + ".";
        }
        JOptionPane.showMessageDialog(mainFrame, text, title, JOptionPane.PLAIN_MESSAGE);
    }

    private void updateBestPrice(int price) {
        int best = getBestPrice();
        if (price <= best || best == -1) {
            bestPrices.put(getSelectedLevelName(), price);
        }
    }

    private int getBestPrice() {
        if (bestPrices.containsKey(getSelectedLevelName())) {
            return bestPrices.get(getSelectedLevelName());
        }
        return -1;
    }

    private void resetPhysicsClock() {
        lastPhysicsTime = System.currentTimeMillis();
    }

    private void newSession() {
        Level level = loadSelectedLevel();
        if (level != null) {
            session = new GameSession(this, box2d, level);
        } else {
            session = null;
        }
    }

    public void checkLevelsPresent() {
        if (session == null) {
            String text = "No level detected.";
            text += "\n" + "Start by creating one by clicking the '" + editorButton.getText() + "'";
            JOptionPane.showMessageDialog(mainFrame, text, "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void updateMousePos(MouseEvent e) {
        mousePos = box2d.pixelToWorld(e.getX(), e.getY());
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }

    @Override
    public void mousePressed(MouseEvent e) {
        updateMousePos(e);
        mouseButton = e.getButton();
        mouseClicked = true;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        updateMousePos(e);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        updateMousePos(e);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        updateMousePos(e);
    }

}
