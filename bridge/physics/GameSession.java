package bridge.physics;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.World;

import bridge.level.Level;
import bridge.physics.beams.Material;
import bridge.physics.car.Car;
import bridge.model.BridgeTopology;
import bridge.save.BridgeSaveFile;
import bridge.save.SimulationRunJson;
import bridge.physics.environment.RiverBank;
import bridge.ui.Box2D;

/**
 * One play session (build + simulate).
 */
public class GameSession {

    /** Fixed integration step (seconds), used by GUI and headless runners. */
    public static final float FIXED_DT = 1f / 60f;

    private SimulationEndListener sessionEndListener;
    private Level level;
    private World world;
    private Bridge bridge;
    private RiverBank riverBank;
    private Car car;
    private int budget;

    private boolean physicsRunning = false;
    private boolean bridgeBuilding = true;
    private boolean finished = false;
    private Material material = Material.ASPHALT;

    private float simTime;
    private boolean recordingEnabled;
    private final ArrayList<SimulationRunJson.Sample> recordingSamples = new ArrayList<>();
    private SessionEndReason sessionEndReason = SessionEndReason.RUNNING;
    /** Set during {@code world.step} when the chassis hits terrain; applied after the step. */
    private boolean pendingTerrainCrash = false;
    private static final float MIN_TIME_BEFORE_TERRAIN_CRASH = 0.1f;

    public GameSession(SimulationEndListener sessionEndListener, Box2D box2d, Level level) {
        this.sessionEndListener = sessionEndListener;
        this.level = level;

        level.centerInView(box2d);
        level.addBoundaryPoints(box2d);

        Vec2 gravity = new Vec2(0.0f, -9.81f);
        world = new World(gravity);

        riverBank = new RiverBank(world, level);
        bridge = new Bridge(world, level);
        car = new Car(world, level);
        budget = level.getBudget();
        world.setContactListener(new CarTerrainContactListener(this));
    }

    /**
     * Called from {@link CarTerrainContactListener} when the car body first contacts terrain.
     */
    public void markPendingTerrainCrash() {
        if (finished || simTime < MIN_TIME_BEFORE_TERRAIN_CRASH) {
            return;
        }
        pendingTerrainCrash = true;
    }

    public Level getLevel() {
        return level;
    }

    /**
     * Bridge save/load is only allowed while simulation is not stepping (not mid-physics).
     */
    public boolean canSaveOrLoadBridge() {
        return !physicsRunning;
    }

    public BridgeSaveFile exportBridgeSave() {
        bridge.stopCreation(world);
        return bridge.exportSnapshot(level);
    }

    public void applyBridgeSave(BridgeSaveFile save) {
        bridge.applyFromSave(world, level, save);
        physicsRunning = false;
        bridgeBuilding = true;
    }

    /**
     * Logical joint/edge graph for genetic algorithms (mutate free joints, then
     * {@link #applyBridgeTopology(BridgeTopology)}).
     */
    public BridgeTopology exportBridgeTopology() {
        bridge.stopCreation(world);
        return bridge.exportTopology(level);
    }

    public void applyBridgeTopology(BridgeTopology topology) {
        bridge.applyTopology(world, level, topology);
        physicsRunning = false;
        bridgeBuilding = true;
    }

    public boolean isPhysicsRunning() {
        return physicsRunning;
    }

    public void setSimulationPhysics(boolean physicsRunning) {
        this.physicsRunning = physicsRunning;
        bridge.stopCreation(world);
        if (bridgeBuilding) {
            bridgeBuilding = false;
        }
    }

    public void toggleSimulationPhysics() {
        setSimulationPhysics(!isPhysicsRunning());
    }

    public void draw(Graphics2D g, Box2D box2d, Vec2 mousePos) {
        riverBank.draw(g, box2d);
        bridge.draw(g, box2d, mousePos, bridgeBuilding);
        car.draw(g, box2d);
    }

    public SessionEndReason getSessionEndReason() {
        return sessionEndReason;
    }

    private void endSessionFinish() {
        boolean success = getTotalPrice() <= getBudget();
        sessionEndReason = SessionEndReason.FINISH;
        sessionEndListener.onSessionEnd(success, getTotalPrice(), sessionEndReason);
        finished = true;
    }

    private void endSessionCrash() {
        sessionEndReason = SessionEndReason.CRASH;
        sessionEndListener.onSessionEnd(false, getTotalPrice(), sessionEndReason);
        finished = true;
    }

    private void endSessionStuck() {
        sessionEndReason = SessionEndReason.STUCK;
        sessionEndListener.onSessionEnd(false, getTotalPrice(), sessionEndReason);
        finished = true;
    }

    public boolean isSessionFinished() {
        return finished;
    }

    public void changeMaterial(Material material) {
        this.material = material;
        bridge.stopCreation(world);
    }

    public int getTotalPrice() {
        return bridge.getTotalPrice();
    }

    public int getBudget() {
        return budget;
    }

    public float getSimTime() {
        return simTime;
    }

    public void setRecordingEnabled(boolean recordingEnabled) {
        this.recordingEnabled = recordingEnabled;
    }

    public boolean isRecordingEnabled() {
        return recordingEnabled;
    }

    public void clearRecording() {
        recordingSamples.clear();
    }

    public boolean hasRecordingSamples() {
        return !recordingSamples.isEmpty();
    }

    public List<SimulationRunJson.Sample> getRecordingSamples() {
        return Collections.unmodifiableList(recordingSamples);
    }

    /**
     * Rear wheel progress in [0,1] along {@link Level#getAnchorSpanMinX()} … {@link Level#getAnchorSpanMaxX()}.
     */
    public float getCurrentAnchorProgress() {
        return level.getAnchorProgressForRearWheelX(car.getRearWheelX());
    }

    public void tickPhysics(Vec2 mousePos, int mouseButton, boolean mouseClicked, float dt) {
        if (physicsRunning) {
            world.step(dt, 10, 8);
            bridge.testBreak(world, dt);
            car.stopIfNeeded();
            simTime += dt;
            if (recordingEnabled) {
                recordingSamples.add(new SimulationRunJson.Sample(simTime, getCurrentAnchorProgress(),
                        car.getRearWheelX(), dt));
            }
            if (!finished) {
                if (pendingTerrainCrash) {
                    endSessionCrash();
                    pendingTerrainCrash = false;
                } else if (car.testStuckNotMoving(dt, simTime)) {
                    endSessionStuck();
                } else if (car.testReachedFinish()) {
                    endSessionFinish();
                }
            }
        } else if (bridgeBuilding) {
            bridge.handleInput(world, mousePos, mouseButton, mouseClicked, material, riverBank);
        }
    }

}
