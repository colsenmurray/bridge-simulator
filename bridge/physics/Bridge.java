package bridge.physics;

import java.awt.Graphics;
import java.io.Serializable;
import java.util.LinkedList;

import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.World;

import bridge.level.Level;
import bridge.physics.beams.AsphaltBeam;
import bridge.physics.beams.Beam;
import bridge.physics.beams.Material;
import bridge.physics.beams.SteelBeam;
import bridge.physics.beams.WoodBeam;
import bridge.physics.environment.RiverBank;
import bridge.physics.nodes.FixedNode;
import bridge.physics.nodes.MobileNode;
import bridge.physics.nodes.Node;
import bridge.ui.Box2D;

/**
 * Bridge structure: graph of nodes and beams.
 */
public class Bridge implements Serializable {

    private LinkedList<Beam> beams;
    private LinkedList<Node> nodes;
    private Beam beamInProgress;
    private Node mobileNodeInProgress;
    private Node nearestNode;

    public Bridge(World world, Level level) {
        beams = new LinkedList<Beam>();
        nodes = new LinkedList<Node>();

        for (Vec2 pos : level.getNodePositions()) {
            nodes.add(new FixedNode(world, pos));
        }
    }

    public void draw(Graphics g, Box2D box2d, Vec2 mousePos, boolean buildingBridge) {
        LinkedList<Beam> drawnBeams = new LinkedList<Beam>();
        for (Node node : nodes) {
            for (Beam beam : node.getLinkedBeams()) {
                if (!drawnBeams.contains(beam) && beam != beamInProgress) {
                    beam.draw(g, box2d);
                    drawnBeams.add(beam);
                }
            }
            if (node != mobileNodeInProgress) {
                node.draw(g, box2d, false);
            }
        }
        if (beamInProgress != null) {
            beamInProgress.draw(g, box2d);
            mobileNodeInProgress.draw(g, box2d, false);
        }
        if (buildingBridge) {
            Node hovered = findNearestNode(mousePos);
            if (hovered != null) {
                hovered.draw(g, box2d, true);
            }
        }
    }

    public void handleInput(World world, Vec2 mousePos, int mouseButton, boolean mouseClicked,
            Material material, RiverBank riverBank) {

        Vec2 clampedMouse = clampMouseForMaxLength(beamInProgress, mousePos);
        nearestNode = findNearestNode(clampedMouse);
        boolean mouseInBank = riverBank.containsPoint(clampedMouse);
        boolean beamOk = isBeamPlacementValid(beamInProgress, nearestNode, mouseInBank);

        if (beamInProgress != null) {

            if (nearestNode != null && beamOk) {
                updatePreview(nearestNode.getPos());
            } else if (!beamInProgress.isWithinMaxLength(mousePos)) {
                updatePreview(clampedMouse);
                nearestNode = null;
            } else {
                updatePreview(mousePos);
            }
        }

        if (mouseClicked) {
            switch (mouseButton) {
                case 1:
                    if (beamOk) {
                        if (nearestNode != null) {
                            beamInProgress.attachTo(world, nearestNode);
                            nodes.remove(mobileNodeInProgress);
                        } else {
                            nearestNode = mobileNodeInProgress;
                        }
                        finishBeam(world);
                    }
                    if (beamInProgress == null && nearestNode != null) {
                        createBeam(world, mousePos, nearestNode, material);
                        clampedMouse = clampMouseForMaxLength(beamInProgress, mousePos);
                        updatePreview(clampedMouse);
                    }
                    break;
                case 3:
                    if (beamInProgress != null) {
                        stopCreation(world);
                    } else {
                        deleteBeamsAtClick(world, mousePos);
                    }
                    break;
            }
        }
    }

    private Vec2 clampMouseForMaxLength(Beam beam, Vec2 mousePos) {
        if (beam == null) {
            return mousePos;
        }
        if (beam.isWithinMaxLength(mousePos)) {
            return mousePos;
        }
        return beamInProgress.clampedEndPosition(mousePos);
    }

    private boolean isBeamPlacementValid(Beam beam, Node nearestNode, boolean mouseInBank) {
        if (beam == null) {
            return false;
        }
        if (!beam.hasMinimumLength()) {
            return false;
        }
        if (nearestNode != null) {
            if (beamExistsBetween(beam, nearestNode)) {
                return false;
            }
            if (beam.getLinkedNodes().contains(nearestNode)) {
                return false;
            }
        } else {
            if (mouseInBank) {
                return false;
            }
        }
        return true;
    }

    private boolean beamExistsBetween(Beam candidate, Node other) {
        if (other == null) {
            return false;
        }
        Node first = candidate.getLinkedNodes().get(0);
        for (Beam beam : first.getLinkedBeams()) {
            if (beam.getLinkedNodes().contains(other)) {
                return true;
            }
        }
        return false;
    }

    public void stopCreation(World world) {
        if (beamInProgress != null) {
            removeBeam(world, beamInProgress);
            beamInProgress = null;
            mobileNodeInProgress = null;
        }
    }

    private void removeBeam(World world, Beam beam) {
        LinkedList<MobileNode> toRemove = beam.removeFromWorld(world);
        beams.remove(beam);
        nodes.removeAll(toRemove);
    }

    private void finishBeam(World world) {
        beamInProgress.activatePhysics();

        for (Node node : beamInProgress.getLinkedNodes()) {
            node.activatePhysics();
            beamInProgress.connect(world, node);
        }

        mobileNodeInProgress = null;
        beamInProgress = null;
    }

    private void updatePreview(Vec2 mousePos) {
        mobileNodeInProgress.setPos(mousePos);
        beamInProgress.adjustPosition();
    }

    private Node findNearestNode(Vec2 mousePos) {
        Node closest = null;
        float minDist = Float.POSITIVE_INFINITY;
        for (Node node : nodes) {
            if (node.testNodeClicked(mousePos) && node != mobileNodeInProgress) {
                float distance = node.distanceToPoint(mousePos);
                if (distance < minDist) {
                    minDist = distance;
                    closest = node;
                }
            }
        }
        return closest;
    }

    private void createBeam(World world, Vec2 clickPos, Node clickedNode, Material material) {
        mobileNodeInProgress = new MobileNode(world, clickPos);
        nodes.add(mobileNodeInProgress);

        Node a = clickedNode;
        Node b = mobileNodeInProgress;

        switch (material) {
            case ASPHALT:
                beamInProgress = new AsphaltBeam(world, a, b);
                break;
            case WOOD:
                beamInProgress = new WoodBeam(world, a, b);
                break;
            case STEEL:
                beamInProgress = new SteelBeam(world, a, b);
                break;
        }

        beams.add(beamInProgress);
    }

    public void testBreak(World world, float dt) {
        for (Beam beam : beams) {
            if (beam != beamInProgress) {
                LinkedList<Node> created = beam.testBreak(world, dt);
                nodes.addAll(created);
            }
        }
    }

    private void deleteBeamsAtClick(World world, Vec2 clickPos) {
        LinkedList<Beam> toRemove = new LinkedList<Beam>();
        for (Beam beam : beams) {
            if (beam.testBeamClicked(clickPos)) {
                removeBeam(world, beam);
                toRemove.add(beam);
            }
        }
        beams.removeAll(toRemove);
    }

    public int getTotalPrice() {
        int total = 0;
        for (Beam beam : beams) {
            total += beam.getPrice();
        }
        return total;
    }

}
