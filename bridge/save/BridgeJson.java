package bridge.save;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jbox2d.common.Vec2;

import bridge.level.Level;
import bridge.model.BridgeEdge;
import bridge.model.BridgeTopology;
import bridge.physics.beams.Material;

/**
 * JSON read/write for {@link BridgeSaveFile}.
 * <p>
 * Version 2 (preferred): {@code joints} + {@code edges}.<br>
 * Legacy: {@code beams} with endpoint coordinates (still loaded; pass {@link Level} for anchor matching).
 */
public final class BridgeJson {

    public static final int FORMAT_VERSION_JOINT_EDGE = 2;

    private static final Pattern VERSION_PATTERN = Pattern.compile("\"version\"\\s*:\\s*(\\d+)");
    private static final Pattern FINGERPRINT_PATTERN = Pattern.compile("\"levelFingerprint\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern COST_PATTERN = Pattern.compile("\"cost\"\\s*:\\s*(\\d+)");
    /** Legacy beam-based records */
    private static final Pattern BEAM_PATTERN = Pattern.compile(
            "\"material\"\\s*:\\s*\"(ASPHALT|WOOD|STEEL)\"\\s*,\\s*"
                    + "\"a\"\\s*:\\s*\\{\\s*\"x\"\\s*:\\s*([^,\\s]+)\\s*,\\s*\"y\"\\s*:\\s*([^}]+?)\\s*\\}\\s*,\\s*"
                    + "\"b\"\\s*:\\s*\\{\\s*\"x\"\\s*:\\s*([^,\\s]+)\\s*,\\s*\"y\"\\s*:\\s*([^}]+?)\\s*\\}");
    /** Joint: "x": n, "y": n, "fixed": true|false */
    private static final Pattern JOINT_PATTERN = Pattern.compile(
            "\"x\"\\s*:\\s*([^,\\s]+)\\s*,\\s*\"y\"\\s*:\\s*([^,\\s]+)\\s*,\\s*\"fixed\"\\s*:\\s*(true|false)");
    /** Edge: "from": n, "to": n, "material": "WOOD" */
    private static final Pattern EDGE_PATTERN = Pattern.compile(
            "\"from\"\\s*:\\s*(\\d+)\\s*,\\s*\"to\"\\s*:\\s*(\\d+)\\s*,\\s*\"material\"\\s*:\\s*\"(ASPHALT|WOOD|STEEL)\"");

    private BridgeJson() {
    }

    public static String toJson(BridgeSaveFile file) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"format\": \"").append(BridgeSaveFile.FORMAT).append("\",\n");
        sb.append("  \"version\": ").append(FORMAT_VERSION_JOINT_EDGE).append(",\n");
        if (file.getLevelFingerprint() != null) {
            sb.append("  \"levelFingerprint\": \"").append(escape(file.getLevelFingerprint())).append("\",\n");
        }
        if (file.getCost() != null) {
            sb.append("  \"cost\": ").append(file.getCost()).append(",\n");
        }
        BridgeTopology t = file.getTopology();
        sb.append("  \"joints\": [\n");
        for (int i = 0; i < t.getJointCount(); i++) {
            Vec2 p = t.getJoint(i);
            sb.append("    { ");
            sb.append("\"x\": ").append(p.x).append(", ");
            sb.append("\"y\": ").append(p.y).append(", ");
            sb.append("\"fixed\": ").append(t.isFixed(i));
            sb.append(" }");
            if (i < t.getJointCount() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ],\n");
        sb.append("  \"edges\": [\n");
        List<BridgeEdge> edges = t.getEdges();
        for (int i = 0; i < edges.size(); i++) {
            BridgeEdge e = edges.get(i);
            sb.append("    { ");
            sb.append("\"from\": ").append(e.getFromJoint()).append(", ");
            sb.append("\"to\": ").append(e.getToJoint()).append(", ");
            sb.append("\"material\": \"").append(e.getMaterial().name()).append("\"");
            sb.append(" }");
            if (i < edges.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * @param levelForLegacy required when loading old {@code beams}-only files (anchor matching); may be null for v2
     */
    public static BridgeSaveFile parse(String json, Level levelForLegacy) throws IOException {
        Matcher v = VERSION_PATTERN.matcher(json);
        int version = FORMAT_VERSION_JOINT_EDGE;
        if (v.find()) {
            version = Integer.parseInt(v.group(1));
        }
        String fingerprint = null;
        Matcher fp = FINGERPRINT_PATTERN.matcher(json);
        if (fp.find()) {
            fingerprint = fp.group(1);
        }
        Integer cost = null;
        Matcher cm = COST_PATTERN.matcher(json);
        if (cm.find()) {
            cost = Integer.parseInt(cm.group(1));
        }
        if (json.contains("\"joints\"")) {
            return parseJointEdgeFormat(version, fingerprint, cost, json);
        }
        return parseLegacyBeamFormat(version, fingerprint, cost, json, levelForLegacy);
    }

    public static BridgeSaveFile readFile(Path path, Level levelForLegacy) throws IOException {
        String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        return parse(text, levelForLegacy);
    }

    private static BridgeSaveFile parseJointEdgeFormat(int version, String fingerprint, Integer cost, String json)
            throws IOException {
        String jointsInner = extractArrayContent(json, "joints");
        String edgesInner = extractArrayContent(json, "edges");
        if (jointsInner == null) {
            throw new IOException("Missing joints array");
        }
        ArrayList<Vec2> joints = new ArrayList<>();
        BitSet fixed = new BitSet();
        Matcher jm = JOINT_PATTERN.matcher(jointsInner);
        int ji = 0;
        while (jm.find()) {
            try {
                float x = Float.parseFloat(jm.group(1).trim());
                float y = Float.parseFloat(jm.group(2).trim());
                boolean isFixed = Boolean.parseBoolean(jm.group(3));
                joints.add(new Vec2(x, y));
                if (isFixed) {
                    fixed.set(ji);
                }
                ji++;
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid joint entry", e);
            }
        }
        ArrayList<BridgeEdge> edges = new ArrayList<>();
        if (edgesInner != null) {
            Matcher em = EDGE_PATTERN.matcher(edgesInner);
            while (em.find()) {
                try {
                    int from = Integer.parseInt(em.group(1));
                    int to = Integer.parseInt(em.group(2));
                    Material mat = Material.valueOf(em.group(3));
                    if (from < 0 || to < 0 || from >= joints.size() || to >= joints.size()) {
                        throw new IOException("Edge references invalid joint index");
                    }
                    if (from == to) {
                        continue;
                    }
                    edges.add(new BridgeEdge(from, to, mat));
                } catch (IllegalArgumentException e) {
                    throw new IOException("Invalid edge entry", e);
                }
            }
        }
        BridgeTopology topology = new BridgeTopology(joints, fixed, edges);
        return new BridgeSaveFile(version, fingerprint, topology, cost);
    }

    /**
     * Returns inner content of the first JSON array for {@code "key": [ ... ]}.
     */
    private static String extractArrayContent(String json, String key) {
        int k = json.indexOf("\"" + key + "\"");
        if (k < 0) {
            return null;
        }
        int lb = json.indexOf('[', k);
        if (lb < 0) {
            return null;
        }
        int depth = 0;
        for (int i = lb; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(lb + 1, i);
                }
            }
        }
        return null;
    }

    private static BridgeSaveFile parseLegacyBeamFormat(int version, String fingerprint, Integer cost, String json,
            Level levelForLegacy)
            throws IOException {
        List<BeamRecord> beams = new ArrayList<>();
        Matcher bm = BEAM_PATTERN.matcher(json);
        while (bm.find()) {
            try {
                Material mat = Material.valueOf(bm.group(1));
                float ax = Float.parseFloat(bm.group(2).trim());
                float ay = Float.parseFloat(bm.group(3).trim());
                float bx = Float.parseFloat(bm.group(4).trim());
                float by = Float.parseFloat(bm.group(5).trim());
                beams.add(new BeamRecord(mat, new Vec2(ax, ay), new Vec2(bx, by)));
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid beam entry in bridge file", e);
            }
        }
        BridgeTopology topology = BridgeTopology.fromBeamRecords(beams, levelForLegacy);
        return new BridgeSaveFile(version, fingerprint, topology, cost);
    }

    public static void writeFile(Path path, BridgeSaveFile file) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, toJson(file).getBytes(StandardCharsets.UTF_8));
    }

}
