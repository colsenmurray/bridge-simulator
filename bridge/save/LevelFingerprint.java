package bridge.save;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.jbox2d.common.Vec2;

import bridge.level.Level;

/**
 * Stable fingerprint of level geometry for bridge save validation.
 */
public final class LevelFingerprint {

    private LevelFingerprint() {
    }

    public static String compute(Level level) {
        StringBuilder sb = new StringBuilder();
        sb.append(level.getBudget());
        for (Vec2 p : level.getTerrainPoints()) {
            sb.append('|').append(p.x).append(',').append(p.y);
        }
        sb.append(';');
        for (Vec2 p : level.getNodePositions()) {
            sb.append('|').append(p.x).append(',').append(p.y);
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return toHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(sb.toString().hashCode());
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder s = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            s.append(String.format("%02x", b & 0xff));
        }
        return s.toString();
    }

}
