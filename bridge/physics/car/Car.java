package bridge.physics.car;

import java.awt.Graphics;

import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.World;

import bridge.level.Level;
import bridge.physics.beams.AsphaltBeam;
import bridge.physics.environment.RiverBank;
import bridge.ui.Box2D;

/**
 * Player car.
 */
public class Car {

    public static final int CATEGORY = 0b0010;
    public static final int MASK = RiverBank.CATEGORY | AsphaltBeam.CATEGORY;

    private Wheel frontWheel;
    private Wheel rearWheel;
    private CarBody body;

    private final Level level;
    private boolean stopped = false;
    private float startX;
    private float finishX;

    /** How long the car has been below {@link #STUCK_SPEED_THRESHOLD} (seconds); reset when moving. */
    private float notMovingAccum;
    /**
     * Body speed (m/s in world) below this counts as "not moving" for the stuck end condition.
     */
    public static final float STUCK_SPEED_THRESHOLD = 0.08f;
    /** Sustained stillness this long ends the run (only after {@link #STUCK_MIN_SIM_TIME}). */
    public static final float STUCK_TIME_SECONDS = 2.5f;
    /** Do not check stuck until the sim has run this long (avoids spawn frame). */
    public static final float STUCK_MIN_SIM_TIME = 0.4f;
    /**
     * Rear wheel must be past this offset from the car start line before stuck can apply,
     * until {@link #forwardMotionStarted} is true (see {@link #FORWARD_MOTION_VX_THRESHOLD}).
     */
    public static final float STUCK_PAST_SPAWN_EPS = 0.15f;
    /**
     * Body +X velocity above this (m/s) once latches {@link #forwardMotionStarted}: the spawn
     * gate for stuck detection is then skipped so a stall still counts as stuck even if the rear
     * wheel never crossed {@link #startX}.
     */
    public static final float FORWARD_MOTION_VX_THRESHOLD = 0.12f;
    /**
     * After this sim time, stuck checks may run even if the car never passed the spawn line,
     * while anchor progress stays at or below {@link #STUCK_UNGATED_MAX_PROGRESS}. Covers a wedge
     * at the start where wheels spin but the body never clears {@link #startX}.
     */
    public static final float STUCK_UNGATED_AFTER_SIM_TIME = 2.0f;
    /** With {@link #STUCK_UNGATED_AFTER_SIM_TIME}, only bypass the spawn line gate below this progress. */
    public static final float STUCK_UNGATED_MAX_PROGRESS = 0.03f;
    /**
     * End as stuck if rear-wheel anchor progress does not increase for this many physics steps
     * (same spawn / min-sim gates as velocity stuck). Catches crawling / jitter with no net gain.
     */
    public static final int STUCK_PLATEAU_STEPS = 200;
    /** Progress must exceed the running peak by this much to count as improvement (float noise). */
    public static final float STUCK_PLATEAU_PROGRESS_EPS = 1e-4f;

    /** Set after the car has clearly moved forward; past-spawn check for stuck is then disabled. */
    private boolean forwardMotionStarted;

    /** Best anchor progress seen since plateau tracking was (re)started; {@code < 0} = uninitialized. */
    private float plateauPeakProgress = -1f;
    /** Consecutive physics steps with no progress improvement over {@link #plateauPeakProgress}. */
    private int stepsWithoutProgressImprovement;

    public Car(World world, Level level) {
        this.level = level;
        startX = level.computeCarStartX();
        finishX = level.computeCarFinishX();

        float wheelSpacing = 6f;
        Vec2 frontWheelPos = new Vec2(startX - wheelSpacing, level.getTerrainPoints().get(1).y + Wheel.RADIUS);
        Vec2 rearWheelPos = frontWheelPos.sub(new Vec2(wheelSpacing, 0f));
        Vec2 bodyPos = new Vec2(0.5f * (rearWheelPos.x + frontWheelPos.x), rearWheelPos.y + 1.4f);

        body = new CarBody(world, bodyPos);
        rearWheel = new Wheel(world, rearWheelPos);
        frontWheel = new Wheel(world, frontWheelPos);

        rearWheel.attachToCar(world, body);
        frontWheel.attachToCar(world, body);
    }

    public float getRearWheelX() {
        return rearWheel.getX();
    }

    public float getRearWheelY() {
        return rearWheel.getY();
    }

    public float getFrontWheelX() {
        return frontWheel.getX();
    }

    public float getFrontWheelY() {
        return frontWheel.getY();
    }

    public void draw(Graphics g, Box2D box2d) {
        body.draw(g, box2d);
        rearWheel.draw(g, box2d);
        frontWheel.draw(g, box2d);
    }

    /**
     * Session ends when rear-wheel {@linkplain Level#getAnchorProgressForRearWheelX progress} has
     * reached the end of the span and the car has nearly stopped horizontally (same velocity gate
     * as before).
     */
    public boolean testReachedFinish() {
        float p = level.getAnchorProgressForRearWheelX(getRearWheelX());
        if (p < 1f - 1e-4f) {
            return false;
        }
        return body.getBody().getLinearVelocity().x <= 0.001f;
    }

    /**
     * True if the car is stuck: nearly still long enough (velocity gate), or no progress
     * improvement for {@link #STUCK_PLATEAU_STEPS} steps. Spawn line can be bypassed after
     * {@link #STUCK_UNGATED_AFTER_SIM_TIME} if progress is still tiny. Not at the goal. Call each tick.
     */
    public boolean testStuckNotMoving(float dt, float simTime) {
        float vx = body.getBody().getLinearVelocity().x;
        if (vx > FORWARD_MOTION_VX_THRESHOLD) {
            forwardMotionStarted = true;
        }

        float p = level.getAnchorProgressForRearWheelX(rearWheel.getX());
        if (p >= 1f - 1e-3f) {
            notMovingAccum = 0f;
            plateauPeakProgress = -1f;
            stepsWithoutProgressImprovement = 0;
            return false;
        }

        final boolean pastSpawnGate = forwardMotionStarted
                || rearWheel.getX() > startX + STUCK_PAST_SPAWN_EPS;
        final boolean stuckChecksActive = pastSpawnGate
                || (simTime >= STUCK_UNGATED_AFTER_SIM_TIME && p <= STUCK_UNGATED_MAX_PROGRESS);

        if (simTime >= STUCK_MIN_SIM_TIME && stuckChecksActive) {
            if (plateauPeakProgress < 0f) {
                plateauPeakProgress = p;
                stepsWithoutProgressImprovement = 0;
            } else if (p > plateauPeakProgress + STUCK_PLATEAU_PROGRESS_EPS) {
                plateauPeakProgress = p;
                stepsWithoutProgressImprovement = 0;
            } else {
                stepsWithoutProgressImprovement++;
            }
            if (stepsWithoutProgressImprovement >= STUCK_PLATEAU_STEPS) {
                return true;
            }
        } else {
            plateauPeakProgress = -1f;
            stepsWithoutProgressImprovement = 0;
        }

        if (simTime < STUCK_MIN_SIM_TIME) {
            notMovingAccum = 0f;
            return false;
        }
        if (!stuckChecksActive) {
            notMovingAccum = 0f;
            return false;
        }
        // Use chassis and wheel-center linear speed (wheels can spin in place while blocked).
        float bodySpeed = body.getBody().getLinearVelocity().length();
        float wheelLin = 0.5f * (rearWheel.getBody().getLinearVelocity().length()
                + frontWheel.getBody().getLinearVelocity().length());
        float speed = Math.max(bodySpeed, wheelLin);
        if (speed < STUCK_SPEED_THRESHOLD) {
            notMovingAccum += dt;
        } else {
            notMovingAccum = 0f;
        }
        return notMovingAccum >= STUCK_TIME_SECONDS;
    }

    public void stopIfNeeded() {
        if (stopped) {
            return;
        }
        float speed = body.getBody().getLinearVelocity().length();
        float progress = level.getAnchorProgressForRearWheelX(rearWheel.getX());
        if (rearWheel.getX() > finishX || progress >= 1f - 1e-4f) {
            rearWheel.stopMotor();
            frontWheel.stopMotor();
            stopped = true;
        } else if (speed <= 0.001f && rearWheel.getX() > startX) {
            rearWheel.stopMotor();
            frontWheel.stopMotor();
            stopped = true;
        }
    }

}
