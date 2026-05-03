package bridge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;

import org.jbox2d.common.Vec2;

import bridge.level.Level;
import bridge.ui.Box2D;

/**
 * Writes {@code res/terrain/&lt;level&gt;.json} with terrain polygon points for Python mutation (joint placement).
 * Applies the same {@link Level#centerInView} and {@link Level#addBoundaryPoints} as
 * {@link bridge.physics.GameSession} so coordinates match headless simulation
 * ({@link HeadlessSimulation}'s {@code Box2D} size).
 */
public final class DumpLevelTerrain {

    private DumpLevelTerrain() {
    }

    public static int run(String[] args) {
        String levelName = null;
        String outPath = null;
        for (int i = 0; i < args.length; i++) {
            if ("--level".equals(args[i]) && i + 1 < args.length) {
                levelName = args[++i];
            } else if ("--output".equals(args[i]) && i + 1 < args.length) {
                outPath = args[++i];
            }
        }
        if (levelName == null) {
            err("Usage: --dump-terrain --level <name> [--output path/to.json]");
            return 1;
        }
        if (outPath == null) {
            outPath = Paths.get("res", "terrain", levelName + ".json").toString();
        }

        Level level;
        try {
            level = Level.loadForHeadless(levelName);
        } catch (IOException | ClassNotFoundException e) {
            err("Failed to load level: " + e.getMessage());
            return 1;
        }

        Box2D box2d = new Box2D(HeadlessSimulation.DEFAULT_PIXEL_WIDTH, HeadlessSimulation.DEFAULT_PIXEL_HEIGHT);
        level.centerInView(box2d);
        level.addBoundaryPoints(box2d);

        LinkedList<Vec2> pts = level.getTerrainPoints();
        StringBuilder sb = new StringBuilder();
        sb.append("{\"level\":\"").append(escapeJson(levelName)).append("\",\"points\":[");
        boolean first = true;
        for (Vec2 p : pts) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('[').append(p.x).append(',').append(p.y).append(']');
        }
        sb.append("]}");

        Path path = Paths.get(outPath);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            err("Failed to write " + outPath + ": " + e.getMessage());
            return 1;
        }
        System.out.println(path.toAbsolutePath());
        return 0;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void err(String msg) {
        System.err.println(msg);
    }
}
