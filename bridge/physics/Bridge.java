package bridge.physics;

import java.awt.Graphics;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.World;

import bridge.level.Level;
import bridge.model.BridgeEdge;
import bridge.model.BridgeTopology;
import bridge.save.BridgeJson;
import bridge.save.BridgeSaveFile;
import bridge.save.LevelFingerprint;
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
 * Bridge structure: joints ({@link Node}) and edges ({@link Beam}), with a logical
 * {@link BridgeTopology} for export and GA-friendly mutations.
 */
public class Bridge implements Serializable {

    private LinkedList<Beam> beams;
    /**
     * Joint index i corresponds to {@code jointNodes.get(i)} (stable for topology export).
     */
    private ArrayList<Node> jointNodes;
    private Beam beamInProgress;
    private Node mobileNodeInProgress;
    private Node nearestNode;

    public Bridge(World world, Level level) {
        beams = new LinkedList<Beam>();
        jointNodes = new ArrayList<>();

        for (Vec2 pos : level.getNodePositions()) {
            jointNodes.add(new FixedNode(world, pos));
        }
    }

    public void draw(Graphics g, Box2D box2d, Vec2 mousePos, boolean buildingBridge) {
        LinkedList<Beam> drawnBeams = new LinkedList<Beam>();
        for (Node node : jointNodes) {
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
                            jointNodes.remove(mobileNodeInProgress);
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
        jointNodes.removeAll(toRemove);
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
        for (Node node : jointNodes) {
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
        jointNodes.add(mobileNodeInProgress);

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
                jointNodes.addAll(created);
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

    /**
     * Logical joint/edge graph (indices align with {@link #jointNodes} order, excluding in-progress preview).
     */
    public BridgeTopology exportTopology(Level level) {
        List<Node> active = new ArrayList<>();
        for (Node n : jointNodes) {
            if (n != mobileNodeInProgress) {
                active.add(n);
            }
        }
        ArrayList<Vec2> jointPositions = new ArrayList<>(active.size());
        java.util.BitSet fixed = new java.util.BitSet();
        Map<Node, Integer> nodeToIndex = new HashMap<>();
        for (int i = 0; i < active.size(); i++) {
            Node n = active.get(i);
            jointPositions.add(n.getPos().clone());
            if (n instanceof FixedNode) {
                fixed.set(i);
            }
            nodeToIndex.put(n, i);
        }
        ArrayList<BridgeEdge> edgeList = new ArrayList<>();
        for (Beam beam : beams) {
            if (beam == beamInProgress) {
                continue;
            }
            Node a = beam.getLinkedNodes().get(0);
            Node b = beam.getLinkedNodes().get(1);
            Integer ia = nodeToIndex.get(a);
            Integer ib = nodeToIndex.get(b);
            if (ia == null || ib == null) {
                continue;
            }
            edgeList.add(new BridgeEdge(ia, ib, materialOf(beam)));
        }
        return new BridgeTopology(jointPositions, fixed, edgeList);
    }

    /**
     * Snapshot for JSON export (world coordinates after level transforms).
     */
    public BridgeSaveFile exportSnapshot(Level level) {
        return new BridgeSaveFile(BridgeJson.FORMAT_VERSION_JOINT_EDGE, LevelFingerprint.compute(level),
                exportTopology(level));
    }

    private static Material materialOf(Beam beam) {
        if (beam instanceof AsphaltBeam) {
            return Material.ASPHALT;
        }
        if (beam instanceof WoodBeam) {
            return Material.WOOD;
        }
        if (beam instanceof SteelBeam) {
            return Material.STEEL;
        }
        throw new IllegalArgumentException("Unknown beam type");
    }

    /**
     * Rebuilds the entire bridge from a logical topology (joint indices + edges).
     */
    public void applyTopology(World world, Level level, BridgeTopology topology) {
        stopCreation(world);
        LinkedList<Beam> beamCopy = new LinkedList<>(beams);
        for (Beam b : beamCopy) {
            removeBeam(world, b);
        }
        beams.clear();
        destroyAllJointNodes(world);
        jointNodes.clear();

        for (int i = 0; i < topology.getJointCount(); i++) {
            Vec2 p = topology.getJoint(i);
            if (topology.isFixed(i)) {
                jointNodes.add(new FixedNode(world, p.clone()));
            } else {
                jointNodes.add(new MobileNode(world, p.clone()));
            }
        }
        for (BridgeEdge e : topology.getEdges()) {
            Node a = jointNodes.get(e.getFromJoint());
            Node b = jointNodes.get(e.getToJoint());
            if (a == b) {
                continue;
            }
            if (nodesHaveBeamBetween(a, b)) {
                continue;
            }
            Beam beam = createBeamOfMaterial(world, a, b, e.getMaterial());
            beams.add(beam);
            beam.activatePhysics();
            for (Node n : beam.getLinkedNodes()) {
                n.activatePhysics();
                beam.connect(world, n);
            }
        }
    }

    private void destroyAllJointNodes(World world) {
        for (Node n : jointNodes) {
            if (n instanceof MobileNode) {
                ((MobileNode) n).destroy(world);
            } else {
                world.destroyBody(n.getBody());
            }
        }
    }

    /**
     * Removes all beams and mobile nodes; fixed nodes from the level remain.
     */
    public void clearAllBeamsAndMobileNodes(World world) {
        stopCreation(world);
        LinkedList<Beam> copy = new LinkedList<>(beams);
        for (Beam b : copy) {
            removeBeam(world, b);
        }
        beams.clear();
        ArrayList<Node> mobileOnly = new ArrayList<>();
        for (Node n : jointNodes) {
            if (n instanceof MobileNode) {
                mobileOnly.add(n);
            }
        }
        for (Node n : mobileOnly) {
            ((MobileNode) n).destroy(world);
            jointNodes.remove(n);
        }
    }

    public void applyFromSave(World world, Level level, BridgeSaveFile save) {
        applyTopology(world, level, save.getTopology());
    }

    private boolean nodesHaveBeamBetween(Node a, Node b) {
        for (Beam beam : a.getLinkedBeams()) {
            if (beam.getLinkedNodes().contains(b)) {
                return true;
            }
        }
        return false;
    }

    private Beam createBeamOfMaterial(World world, Node a, Node b, Material material) {
        switch (material) {
            case ASPHALT:
                return new AsphaltBeam(world, a, b);
            case WOOD:
                return new WoodBeam(world, a, b);
            case STEEL:
                return new SteelBeam(world, a, b);
            default:
                throw new IllegalArgumentException("Unknown material");
        }
    }

}
