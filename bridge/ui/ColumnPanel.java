package bridge.ui;

import java.awt.Component;

import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * Vertical column panel (BoxLayout wrapper).
 */
public class ColumnPanel extends JPanel {

    public ColumnPanel() {
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    }

    @Override
    public Component add(Component comp) {
        if (comp instanceof JComponent) {
            ((JComponent) comp).setAlignmentX(Component.CENTER_ALIGNMENT);
        }
        return super.add(comp);
    }

}
