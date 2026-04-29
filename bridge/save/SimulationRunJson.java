package bridge.save;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import bridge.physics.SessionEndReason;

/**
 * JSON export for recorded simulation samples (time series of car progress along anchor span).
 */
public final class SimulationRunJson {

    public static final String FORMAT = "bridge-simulator-simulation-run";
    public static final int VERSION = 3;

    private SimulationRunJson() {
    }

    /**
     * One physics tick while recording was enabled.
     */
    public static final class Sample {
        public final float t;
        public final float progress;
        public final float rearWheelX;
        public final float dt;

        public Sample(float t, float progress, float rearWheelX, float dt) {
            this.t = t;
            this.progress = progress;
            this.rearWheelX = rearWheelX;
            this.dt = dt;
        }
    }

    /**
     * Outcome of the play session, written on every run save (GUI or headless).
     */
    public static final class RunEnd {
        public final boolean sessionFinished;
        public final String endReason;
        public final boolean crashed;

        public RunEnd(boolean sessionFinished, String endReason, boolean crashed) {
            this.sessionFinished = sessionFinished;
            this.endReason = endReason;
            this.crashed = crashed;
        }
    }

    /**
     * When the run is produced by {@code HeadlessSimulation}; adds {@code headless: true} and step counts.
     */
    public static final class HeadlessTimesteps {
        public final int maxTimesteps;
        public final int timestepsRun;

        public HeadlessTimesteps(int maxTimesteps, int timestepsRun) {
            this.maxTimesteps = maxTimesteps;
            this.timestepsRun = timestepsRun;
        }
    }

    public static void writeFile(Path path, String levelName, float anchorMinX, float anchorMaxX,
            List<Sample> samples) throws IOException {
        writeFile(path, levelName, anchorMinX, anchorMaxX, samples, null, null);
    }

    public static void writeFile(Path path, String levelName, float anchorMinX, float anchorMaxX, List<Sample> samples,
            RunEnd runEnd) throws IOException {
        writeFile(path, levelName, anchorMinX, anchorMaxX, samples, runEnd, null);
    }

    public static void writeFile(Path path, String levelName, float anchorMinX, float anchorMaxX, List<Sample> samples,
            RunEnd runEnd, HeadlessTimesteps headless) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, toJson(levelName, anchorMinX, anchorMaxX, samples, runEnd, headless).getBytes(
                StandardCharsets.UTF_8));
    }

    /**
     * Map session state to JSON fields (GUI or any caller with {@link GameSession}).
     */
    public static RunEnd runEndFromSession(boolean sessionFinished, SessionEndReason reason) {
        if (!sessionFinished) {
            return new RunEnd(false, "running", false);
        }
        if (reason == SessionEndReason.CRASH) {
            return new RunEnd(true, "crash", true);
        }
        if (reason == SessionEndReason.STUCK) {
            return new RunEnd(true, "stuck", false);
        }
        if (reason == SessionEndReason.FINISH) {
            return new RunEnd(true, "finish", false);
        }
        if (reason == SessionEndReason.MAX_STEPS) {
            return new RunEnd(false, "max_steps", false);
        }
        if (reason == SessionEndReason.RUNNING) {
            return new RunEnd(false, "running", false);
        }
        return new RunEnd(true, "unknown", false);
    }

    private static String toJson(String levelName, float anchorMinX, float anchorMaxX, List<Sample> samples,
            RunEnd runEnd, HeadlessTimesteps headless) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"format\": \"").append(FORMAT).append("\",\n");
        sb.append("  \"version\": ").append(VERSION).append(",\n");
        sb.append("  \"levelName\": \"").append(escape(levelName != null ? levelName : "")).append("\",\n");
        if (runEnd != null) {
            if (headless != null) {
                sb.append("  \"headless\": true,\n");
                sb.append("  \"maxTimesteps\": ").append(headless.maxTimesteps).append(",\n");
                sb.append("  \"timestepsRun\": ").append(headless.timestepsRun).append(",\n");
            }
            sb.append("  \"sessionFinished\": ").append(runEnd.sessionFinished).append(",\n");
            sb.append("  \"endReason\": \"").append(escape(runEnd.endReason != null ? runEnd.endReason : ""))
                    .append("\",\n");
            sb.append("  \"crashed\": ").append(runEnd.crashed).append(",\n");
        }
        sb.append("  \"anchorSpan\": {\n");
        sb.append("    \"minX\": ").append(anchorMinX).append(",\n");
        sb.append("    \"maxX\": ").append(anchorMaxX).append("\n");
        sb.append("  },\n");
        sb.append("  \"samples\": [\n");
        for (int i = 0; i < samples.size(); i++) {
            Sample s = samples.get(i);
            sb.append("    { ");
            sb.append("\"t\": ").append(s.t).append(", ");
            sb.append("\"progress\": ").append(s.progress).append(", ");
            sb.append("\"rearWheelX\": ").append(s.rearWheelX).append(", ");
            sb.append("\"dt\": ").append(s.dt);
            sb.append(" }");
            if (i < samples.size() - 1) {
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
}
