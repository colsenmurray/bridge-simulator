package bridge.model;

import bridge.physics.beams.Material;

/**
 * Logical edge between two joints (by index into {@link BridgeTopology#getJointCount()}).
 */
public final class BridgeEdge {

    private final int fromJoint;
    private final int toJoint;
    private final Material material;
    /** Stable id for save/export and GA bookkeeping; null/empty means "unassigned". */
    private final String uuid;

    public BridgeEdge(int fromJoint, int toJoint, Material material) {
        this(fromJoint, toJoint, material, null);
    }

    public BridgeEdge(int fromJoint, int toJoint, Material material, String uuid) {
        if (fromJoint == toJoint) {
            throw new IllegalArgumentException("Self-loop edge");
        }
        this.fromJoint = fromJoint;
        this.toJoint = toJoint;
        this.material = material;
        this.uuid = uuid;
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

    public String getUuid() {
        return uuid;
    }

}
