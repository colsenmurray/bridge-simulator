package bridge.physics;

import java.awt.Graphics2D;

import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.World;

import bridge.level.Level;
import bridge.physics.beams.Material;
import bridge.physics.car.Car;
import bridge.model.BridgeTopology;
import bridge.save.BridgeSaveFile;
import bridge.physics.environment.RiverBank;
import bridge.ui.Box2D;
import bridge.ui.GamePanel;

/**
 * One play session (build + simulate).
 */
public class GameSession {

    private GamePanel gamePanel;
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

    public GameSession(GamePanel gamePanel, Box2D box2d, Level level) {
        this.gamePanel = gamePanel;
        this.level = level;

        level.centerInView(box2d);
        level.addBoundaryPoints(box2d);

        Vec2 gravity = new Vec2(0.0f, -9.81f);
        world = new World(gravity);

        riverBank = new RiverBank(world, level);
        bridge = new Bridge(world, level);
        car = new Car(world, level);
        budget = level.getBudget();
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

    public void tickPhysics(Vec2 mousePos, int mouseButton, boolean mouseClicked, float dt) {
        if (physicsRunning) {
            world.step(dt, 10, 8);
            bridge.testBreak(world, dt);
            car.stopIfNeeded();
            if (!finished && car.testReachedFinish()) {
                endSession();
            }
        } else if (bridgeBuilding) {
            bridge.handleInput(world, mousePos, mouseButton, mouseClicked, material, riverBank);
        }
    }

    private void endSession() {
        boolean success = getTotalPrice() <= getBudget();
        gamePanel.onSessionEnd(success, getTotalPrice());
        finished = true;
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

}
