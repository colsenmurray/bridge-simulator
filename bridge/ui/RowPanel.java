package bridge.ui;

import java.awt.FlowLayout;

import javax.swing.JPanel;

/**
 * Horizontal row panel (FlowLayout wrapper).
 */
public class RowPanel extends JPanel {

    public RowPanel() {
        setOpaque(false);
    }

    public RowPanel(int gapX, int gapY) {
        this();
        setLayout(new FlowLayout(FlowLayout.CENTER, gapX, gapY));
    }

}
