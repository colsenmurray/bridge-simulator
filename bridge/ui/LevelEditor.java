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

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.MouseInputListener;

import org.jbox2d.common.Vec2;

import bridge.level.Level;

/**
 * Level editor panel.
 */
public class LevelEditor extends JPanel implements ActionListener, MouseInputListener {

    private MainFrame mainFrame;
    private Box2D box2d;
    private Level level;

    private JButton saveButton;
    private JButton loadButton;
    private JButton deleteButton;
    private JTextField levelNameField;
    private JTextField budgetField;
    private JButton cancelButton;
    private JButton clearButton;
    private JButton backToGameButton;

    public LevelEditor(MainFrame mainFrame, Box2D box2d, int refreshRate) {
        this.mainFrame = mainFrame;
        this.box2d = box2d;
        buildUi();

        level = new Level();

        addMouseListener(this);
        addMouseMotionListener(this);
    }

    private void buildUi() {
        this.setLayout(new BorderLayout());
        this.setOpaque(false);

        JPanel topRow = new RowPanel(box2d.getPixelWidth() / 20, box2d.getPixelHeight() / 100);
        this.add(topRow, BorderLayout.PAGE_START);

        JPanel fileColumn = new ColumnPanel();
        topRow.add(fileColumn);
        JLabel fileLabel = new JLabel("File");
        fileColumn.add(fileLabel);
        JPanel fileRow = new RowPanel();
        fileColumn.add(fileRow);
        saveButton = new JButton("Save");
        saveButton.addActionListener(this);
        fileRow.add(saveButton);
        loadButton = new JButton("Load");
        loadButton.addActionListener(this);
        fileRow.add(loadButton);
        deleteButton = new JButton("Delete");
        deleteButton.addActionListener(this);
        fileRow.add(deleteButton);

        JPanel nameColumn = new ColumnPanel();
        topRow.add(nameColumn);
        JLabel nameLabel = new JLabel("Name");
        nameColumn.add(nameLabel);
        JPanel nameRow = new RowPanel();
        nameColumn.add(nameRow);
        levelNameField = new JTextField(6);
        nameRow.add(levelNameField);

        JPanel budgetColumn = new ColumnPanel();
        topRow.add(budgetColumn);
        JLabel budgetLabel = new JLabel("Budget");
        budgetColumn.add(budgetLabel);
        JPanel budgetRow = new RowPanel();
        budgetColumn.add(budgetRow);
        budgetField = new JTextField("0", 5);
        budgetRow.add(budgetField);

        JPanel createColumn = new ColumnPanel();
        topRow.add(createColumn);
        JLabel createLabel = new JLabel("Create");
        createColumn.add(createLabel);
        JPanel createRow = new RowPanel();
        createColumn.add(createRow);
        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(this);
        createRow.add(cancelButton);
        clearButton = new JButton("Clear");
        clearButton.addActionListener(this);
        createRow.add(clearButton);

        JPanel gameColumn = new ColumnPanel();
        topRow.add(gameColumn);
        JLabel gameLabel = new JLabel("Game");
        gameColumn.add(gameLabel);
        JPanel gameRow = new RowPanel();
        gameColumn.add(gameRow);
        backToGameButton = new JButton("Back to Game");
        backToGameButton.addActionListener(this);
        gameRow.add(backToGameButton);

        JPanel bottomPanel = new RowPanel(box2d.getPixelWidth() / 20, box2d.getPixelHeight() / 50);
        this.add(bottomPanel, BorderLayout.PAGE_END);

        JPanel pointColumn = new ColumnPanel();
        bottomPanel.add(pointColumn);
        JLabel pointLabel = new JLabel("Left click:");
        pointColumn.add(pointLabel);
        JLabel pointDesc = new JLabel("add a point");
        pointColumn.add(pointDesc);

        JPanel linkColumn = new ColumnPanel();
        bottomPanel.add(linkColumn);
        JLabel linkLabel = new JLabel("Right click:");
        linkColumn.add(linkLabel);
        JLabel linkDesc = new JLabel("add a link");
        linkColumn.add(linkDesc);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Toolkit.getDefaultToolkit().sync();

        g.setColor(Color.decode("#55a3d4"));
        g.fillRect(0, 0, getWidth(), getHeight());

        level.draw(g, box2d);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();

        if (source == saveButton) {
            level.save(mainFrame, getLevelName(), budgetField.getText());
        }
        if (source == loadButton) {
            Level loaded = Level.load(mainFrame, getLevelName());
            if (loaded != null) {
                level = loaded;
            }
            budgetField.setText(Integer.toString(level.getBudget()));
        }
        if (source == deleteButton) {
            Level.delete(mainFrame, getLevelName());
        }
        if (source == cancelButton) {
            level.undo();
        }
        if (source == clearButton) {
            level = new Level();
        }
        if (source == backToGameButton) {
            mainFrame.showGame();
        }
        repaint();
    }

    private String getLevelName() {
        return levelNameField.getText();
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
        Vec2 mousePos = box2d.pixelToWorld(e.getX(), e.getY());
        switch (e.getButton()) {
            case 1:
                level.addPoint(mousePos);
                break;
            case 2:
                level.undo();
                break;
            case 3:
                level.addNodePosition(mousePos);
                break;
        }
        repaint();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseDragged(MouseEvent e) {
    }

    @Override
    public void mouseMoved(MouseEvent e) {
    }

}
