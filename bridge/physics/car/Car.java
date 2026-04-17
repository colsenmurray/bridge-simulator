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
