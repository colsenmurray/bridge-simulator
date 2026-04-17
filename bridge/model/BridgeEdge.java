package bridge.model;

import bridge.physics.beams.Material;

/**
 * Logical edge between two joints (by index into {@link BridgeTopology#getJointCount()}).
 */
public final class BridgeEdge {

    private final int fromJoint;
    private final int toJoint;
    private final Material material;

    public BridgeEdge(int fromJoint, int toJoint, Material material) {
        if (fromJoint == toJoint) {
            throw new IllegalArgumentException("Self-loop edge");
        }
        this.fromJoint = fromJoint;
        this.toJoint = toJoint;
        this.material = material;
    }

    public int getFromJoint() {
        return fromJoint;
    }

    public int getToJoint() {
        return toJoint;
    }

    public Material getMaterial() {
        return material;
    }

}
