package bridge;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.jbox2d.common.Vec2;

import bridge.level.Level;
import bridge.physics.GameSession;
import bridge.physics.SimulationEndListener;
import bridge.save.BridgeJson;
import bridge.save.BridgeSaveFile;
import bridge.save.SimulationRunJson;
import bridge.ui.Box2D;

/**
 * Runs physics without Swing: load level + bridge, step up to a limit, write {@link SimulationRunJson}.
 */
public final class HeadlessSimulation {

    public static final int DEFAULT_PIXEL_WIDTH = 960;
    public static final int DEFAULT_PIXEL_HEIGHT = 540;

    private static final Vec2 NO_MOUSE = new Vec2();

    private HeadlessSimulation() {
    }

    /**
     * @return process exit code (0 = success, 1 = error)
     */
    public static int run(String[] args) {
        String levelName = null;
        String bridgePath = null;
        Integer maxSteps = null;
        for (int i = 0; i < args.length; i++) {
            if ("--level".equals(args[i]) && i + 1 < args.length) {
                levelName = args[++i];
            } else if ("--bridge".equals(args[i]) && i + 1 < args.length) {
                bridgePath = args[++i];
            } else if ("--max-steps".equals(args[i]) && i + 1 < args.length) {
                try {
                    maxSteps = Integer.parseInt(args[++i]);
                } catch (NumberFormatException e) {
                    err("Invalid --max-steps value");
                    return 1;
                }
            }
        }
        if (levelName == null || bridgePath == null || maxSteps == null) {
            err("Usage: --headless --level <name> --bridge <path/to/bridge.json> --max-steps <n>");
            return 1;
        }
        if (maxSteps < 1) {
            err("--max-steps must be at least 1");
            return 1;
        }

        Level level;
        try {
            level = Level.loadForHeadless(levelName);
        } catch (IOException | ClassNotFoundException e) {
            err("Failed to load level: " + e.getMessage());
            return 1;
        }

        BridgeSaveFile bridgeSave;
        Path bridge = Paths.get(bridgePath);
        try {
            bridgeSave = BridgeJson.readFile(bridge, level);
        } catch (IOException e) {
            err("Failed to load bridge: " + e.getMessage());
            return 1;
        }

        SimulationEndListener noop = (success, price) -> {
        };

        Box2D box2d = new Box2D(DEFAULT_PIXEL_WIDTH, DEFAULT_PIXEL_HEIGHT);
        GameSession session = new GameSession(noop, box2d, level);
        session.applyBridgeSave(bridgeSave);
        session.setRecordingEnabled(true);
        session.setSimulationPhysics(true);

        int step = 0;
        for (; step < maxSteps && !session.isSessionFinished(); step++) {
            session.tickPhysics(NO_MOUSE, 0, false, GameSession.FIXED_DT);
        }

        String uuid = UUID.randomUUID().toString();
        Path out = Paths.get("res", "simulations", uuid + ".json");
        try {
            SimulationRunJson.writeFile(out, levelName, level.getAnchorSpanMinX(), level.getAnchorSpanMaxX(),
                    session.getRecordingSamples(),
                    new SimulationRunJson.HeadlessMeta(maxSteps, step, session.isSessionFinished()));
        } catch (IOException e) {
            err("Failed to write output: " + e.getMessage());
            return 1;
        }

        System.out.println(out.toAbsolutePath());
        return 0;
    }

    private static void err(String msg) {
        System.err.println(msg);
    }
}
