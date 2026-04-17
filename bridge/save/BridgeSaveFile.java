package bridge.save;

import bridge.model.BridgeTopology;

/**
 * Root object for bridge JSON save files (joint/edge topology).
 */
public final class BridgeSaveFile {

    public static final String FORMAT = "bridge-simulator-bridge";

    private final int version;
    private final String levelFingerprint;
    private final BridgeTopology topology;
    /** Total bridge price in dollars when saved; null if not present in file (legacy). */
    private final Integer cost;

    public BridgeSaveFile(int version, String levelFingerprint, BridgeTopology topology, Integer cost) {
        this.version = version;
        this.levelFingerprint = levelFingerprint;
        this.topology = topology;
        this.cost = cost;
    }

    /**
     * Legacy constructor for parsers that do not have a stored cost.
     */
    public BridgeSaveFile(int version, String levelFingerprint, BridgeTopology topology) {
        this(version, levelFingerprint, topology, null);
    }

    public int getVersion() {
        return version;
    }

    public String getLevelFingerprint() {
        return levelFingerprint;
    }

    public BridgeTopology getTopology() {
        return topology;
    }

    /**
     * Total price at save time, or null for older files without {@code cost}.
     */
    public Integer getCost() {
        return cost;
    }

}
