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

    public BridgeSaveFile(int version, String levelFingerprint, BridgeTopology topology) {
        this.version = version;
        this.levelFingerprint = levelFingerprint;
        this.topology = topology;
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

}
