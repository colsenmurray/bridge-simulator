package bridge.model;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

import org.jbox2d.common.Vec2;

import bridge.level.Level;
import bridge.save.BeamRecord;

/**
 * Joint/edge graph in world space for editing, save/load, and genetic algorithms.
 * Joint index {@code i} is fixed (anchored to the level) iff {@link #isFixed(int)}.
 */
public final class BridgeTopology {

    private static final float POSITION_EPS = 0.05f;

    private final ArrayList<Vec2> jointPositions;
    private final BitSet fixedJoints;
    private final ArrayList<BridgeEdge> edges;

    public BridgeTopology(ArrayList<Vec2> jointPositions, BitSet fixedJoints, ArrayList<BridgeEdge> edges) {
        this.jointPositions = new ArrayList<>(jointPositions.size());
        for (Vec2 p : jointPositions) {
            this.jointPositions.add(p.clone());
        }
        this.fixedJoints = (BitSet) fixedJoints.clone();
        this.edges = new ArrayList<>(edges);
    }

    public int getJointCount() {
        return jointPositions.size();
    }

    public Vec2 getJoint(int index) {
        return jointPositions.get(index).clone();
    }

    public boolean isFixed(int jointIndex) {
        return fixedJoints.get(jointIndex);
    }

    public int getFixedJointCount() {
        return fixedJoints.cardinality();
    }

    public List<BridgeEdge> getEdges() {
        return Collections.unmodifiableList(edges);
    }

    /**
     * Copy with a small position change on one joint (ignored for fixed joints).
     */
    public BridgeTopology withJointDelta(int jointIndex, Vec2 delta) {
        if (jointIndex < 0 || jointIndex >= jointPositions.size()) {
            throw new IndexOutOfBoundsException(jointIndex);
        }
        ArrayList<Vec2> next = new ArrayList<>(jointPositions.size());
        for (int i = 0; i < jointPositions.size(); i++) {
            Vec2 p = jointPositions.get(i).clone();
            if (i == jointIndex && !fixedJoints.get(i)) {
                p.addLocal(delta);
            }
            next.add(p);
        }
        return new BridgeTopology(next, fixedJoints, edges);
    }

    /**
     * Replace a free joint position (throws if joint is fixed).
     */
    public BridgeTopology withJointPosition(int jointIndex, Vec2 position) {
        if (fixedJoints.get(jointIndex)) {
            throw new IllegalArgumentException("Cannot move fixed joint " + jointIndex);
        }
        ArrayList<Vec2> next = new ArrayList<>(jointPositions.size());
        for (int i = 0; i < jointPositions.size(); i++) {
            if (i == jointIndex) {
                next.add(position.clone());
            } else {
                next.add(jointPositions.get(i).clone());
            }
        }
        return new BridgeTopology(next, fixedJoints, edges);
    }

    public List<BeamRecord> toBeamRecords() {
        List<BeamRecord> list = new ArrayList<>();
        for (BridgeEdge e : edges) {
            Vec2 a = jointPositions.get(e.getFromJoint());
            Vec2 b = jointPositions.get(e.getToJoint());
            list.add(new BeamRecord(e.getMaterial(), a.clone(), b.clone()));
        }
        return list;
    }

    /**
     * Merges beam endpoints into joints; marks joints fixed when they match a level anchor.
     */
    public static BridgeTopology fromBeamRecords(List<BeamRecord> records, Level level) {
        ArrayList<Vec2> joints = new ArrayList<>();
        BitSet fixed = new BitSet();
        ArrayList<BridgeEdge> edgeList = new ArrayList<>();

        for (BeamRecord rec : records) {
            int ia = indexOfOrAdd(joints, fixed, level, rec.getA());
            int ib = indexOfOrAdd(joints, fixed, level, rec.getB());
            if (ia == ib) {
                continue;
            }
            if (hasEdge(edgeList, ia, ib)) {
                continue;
            }
            edgeList.add(new BridgeEdge(ia, ib, rec.getMaterial()));
        }
        return new BridgeTopology(joints, fixed, edgeList);
    }

    private static boolean hasEdge(List<BridgeEdge> edgeList, int a, int b) {
        for (BridgeEdge e : edgeList) {
            if (e.getFromJoint() == a && e.getToJoint() == b) {
                return true;
            }
            if (e.getFromJoint() == b && e.getToJoint() == a) {
                return true;
            }
        }
        return false;
    }

    private static int indexOfOrAdd(ArrayList<Vec2> joints, BitSet fixed, Level level, Vec2 pos) {
        for (int i = 0; i < joints.size(); i++) {
            if (joints.get(i).sub(pos).length() < POSITION_EPS) {
                return i;
            }
        }
        int idx = joints.size();
        joints.add(pos.clone());
        if (matchesLevelAnchor(level, pos)) {
            fixed.set(idx);
        }
        return idx;
    }

    private static boolean matchesLevelAnchor(Level level, Vec2 pos) {
        if (level == null) {
            return false;
        }
        for (Vec2 anchor : level.getNodePositions()) {
            if (anchor.sub(pos).length() < POSITION_EPS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Empty topology: only level anchors as joints, no edges.
     */
    public static BridgeTopology empty(Level level) {
        ArrayList<Vec2> joints = new ArrayList<>();
        BitSet fixed = new BitSet();
        for (Vec2 p : level.getNodePositions()) {
            int i = joints.size();
            joints.add(p.clone());
            fixed.set(i);
        }
        return new BridgeTopology(joints, fixed, new ArrayList<>());
    }

}
