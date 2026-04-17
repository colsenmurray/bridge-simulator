package bridge.save;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * JSON export for recorded simulation samples (time series of car progress along anchor span).
 */
public final class SimulationRunJson {

    public static final String FORMAT = "bridge-simulator-simulation-run";
    public static final int VERSION = 1;

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

    public static void writeFile(Path path, String levelName, float anchorMinX, float anchorMaxX,
            List<Sample> samples) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, toJson(levelName, anchorMinX, anchorMaxX, samples).getBytes(StandardCharsets.UTF_8));
    }

    private static String toJson(String levelName, float anchorMinX, float anchorMaxX, List<Sample> samples) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"format\": \"").append(FORMAT).append("\",\n");
        sb.append("  \"version\": ").append(VERSION).append(",\n");
        sb.append("  \"levelName\": \"").append(escape(levelName != null ? levelName : "")).append("\",\n");
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
