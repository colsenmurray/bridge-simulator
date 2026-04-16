package bridge.level;

import java.awt.Color;
import java.awt.Graphics2D;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;

import javax.swing.JOptionPane;

import org.jbox2d.common.Vec2;

import bridge.physics.nodes.Node;
import bridge.ui.Box2D;
import bridge.ui.MainFrame;

/**
 * Level data: terrain polyline and node positions.
 */
public class Level implements Serializable {

    public static final Path LEVELS_PATH = Paths.get("res", "niveaux");
    private static final long serialVersionUID = 5014471600563766405L;

    /**
     * Field names must match legacy {@code ponts.niveau.Niveau} for Java serialization compatibility.
     */
    private LinkedList<Vec2> posCoins;
    private LinkedList<Vec2> posLiaisons;
    int budget = 0;

    public Level() {
        posCoins = new LinkedList<Vec2>();
        posLiaisons = new LinkedList<Vec2>();
    }

    public LinkedList<Vec2> getNodePositions() {
        return posLiaisons;
    }

    public void draw(Graphics2D g, Box2D box2d) {
        g.setColor(Color.BLACK);
        if (!posCoins.isEmpty()) {
            Vec2 prev = posCoins.getFirst();
            for (Vec2 pos : posCoins) {
                int x1 = box2d.worldToPixelX(prev.x);
                int y1 = box2d.worldToPixelY(prev.y);
                int x2 = box2d.worldToPixelX(pos.x);
                int y2 = box2d.worldToPixelY(pos.y);
                g.drawLine(x1, y1, x2, y2);
                prev = pos;
            }
        }

        int r = box2d.worldToPixel(Node.RADIUS);
        for (Vec2 pos : posLiaisons) {
            int x = box2d.worldToPixelX(pos.x);
            int y = box2d.worldToPixelY(pos.y);
            g.fillOval(x - r, y - r, r * 2, r * 2);
        }
    }

    public void addPoint(Vec2 mousePos) {
        posCoins.add(mousePos);
    }

    public void addNodePosition(Vec2 mousePos) {
        addPoint(mousePos);
        posLiaisons.add(mousePos);
    }

    private boolean isValid() {
        return !posCoins.isEmpty();
    }

    public void addBoundaryPoints(Box2D box2d) {
        float worldWidth = box2d.getWorldWidth();
        float worldHeight = box2d.getWorldHeight();

        Vec2 leftEdge = new Vec2(-worldWidth, posCoins.getFirst().y);
        posCoins.addFirst(leftEdge);
        Vec2 rightEdge = new Vec2(2 * worldWidth, posCoins.getLast().y);
        posCoins.add(rightEdge);

        Vec2 topLeft = new Vec2(-worldWidth, -worldHeight);
        posCoins.addFirst(topLeft);
        Vec2 topRight = new Vec2(2 * worldWidth, -worldHeight);
        posCoins.add(topRight);
    }

    public void setBudget(int budget) {
        this.budget = budget;
    }

    public int getBudget() {
        return budget;
    }

    public void undo() {
        if (!posCoins.isEmpty()) {
            Vec2 last = posCoins.getLast();
            posCoins.removeLast();
            if (!posLiaisons.isEmpty()) {
                if (posLiaisons.getLast() == last) {
                    posLiaisons.removeLast();
                }
            }
        }
    }

    public void save(MainFrame mainFrame, String levelName, String budgetText) {
        String path = pathForLevel(levelName);
        String title = "Sauvegarde niveau";

        if (!isValid()) {
            JOptionPane.showMessageDialog(mainFrame, "Le niveau est invalide", title,
                    JOptionPane.ERROR_MESSAGE);
        }
        try {
            budget = Integer.parseInt(budgetText);
            FileOutputStream fileOut = new FileOutputStream(path);
            ObjectOutputStream objectOut = new ObjectOutputStream(fileOut);
            objectOut.writeObject(this);
            objectOut.close();
            fileOut.close();
            JOptionPane.showMessageDialog(mainFrame, "Niveau sauvegardé", title,
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException i) {
            JOptionPane.showMessageDialog(mainFrame, "Le budget est invalide", title,
                    JOptionPane.ERROR_MESSAGE);
        } catch (FileNotFoundException i) {
            JOptionPane.showMessageDialog(mainFrame, "Le nom de niveau est invalide", title,
                    JOptionPane.ERROR_MESSAGE);
        } catch (IOException i) {
            i.printStackTrace();
        }
    }

    public static Level load(MainFrame mainFrame, String levelName) {
        if (levelName == null) {
            return null;
        }
        String path = pathForLevel(levelName);
        String title = "Charge niveau";
        Level level = null;
        try {
            FileInputStream fileIn = new FileInputStream(path);
            ObjectInputStream objectIn = createLevelInputStream(fileIn);
            level = (Level) objectIn.readObject();
            objectIn.close();
            fileIn.close();

        } catch (FileNotFoundException i) {
            JOptionPane.showMessageDialog(mainFrame, "Niveau introuvable", title, JOptionPane.ERROR_MESSAGE);
        } catch (IOException i) {
            i.printStackTrace();
        } catch (ClassNotFoundException i) {
            i.printStackTrace();
        }

        return level;
    }

    public LinkedList<Vec2> getTerrainPoints() {
        return posCoins;
    }

    /**
     * Maps legacy serialized class {@code ponts.niveau.Niveau} to {@link Level} (same instance fields).
     */
    private static ObjectInputStream createLevelInputStream(InputStream in) throws IOException {
        return new ObjectInputStream(in) {
            @Override
            protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
                ObjectStreamClass desc = super.readClassDescriptor();
                if ("ponts.niveau.Niveau".equals(desc.getName())) {
                    return ObjectStreamClass.lookup(Level.class);
                }
                return desc;
            }

            @Override
            protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                if ("ponts.niveau.Niveau".equals(desc.getName())) {
                    return Level.class;
                }
                return super.resolveClass(desc);
            }
        };
    }

    private static String pathForLevel(String levelName) {
        return LEVELS_PATH.resolve(levelName).toString();
    }

    public static void delete(MainFrame mainFrame, String levelName) {
        File file = new File(pathForLevel(levelName));
        String title = "Suppression niveau";
        if (file.delete()) {
            JOptionPane.showMessageDialog(mainFrame, "Niveau supprimé", title,
                    JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(mainFrame, "Niveau introuvable", title,
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    public void centerInView(Box2D box2d) {
        Vec2 left = posCoins.getFirst();
        Vec2 right = posCoins.getLast();

        float deltaX = box2d.getWorldWidth() / 2 - (left.x + right.x) / 2;
        float deltaY = 1.1f * box2d.getWorldHeight() / 2 - (right.y + left.y) / 2;
        for (Vec2 point : posCoins) {
            point.addLocal(deltaX, deltaY);
        }
    }

    public float computeCarStartX() {
        return posCoins.get(2).x;
    }

    public float computeCarFinishX() {
        return posCoins.get(posCoins.size() - 3).x;
    }

}
