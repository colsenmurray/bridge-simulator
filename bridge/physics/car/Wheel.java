package bridge.physics.car;

import java.awt.Color;
import java.awt.Graphics;

import org.jbox2d.collision.shapes.CircleShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.BodyDef;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.FixtureDef;
import org.jbox2d.dynamics.World;
import org.jbox2d.dynamics.joints.RevoluteJoint;
import org.jbox2d.dynamics.joints.RevoluteJointDef;

import bridge.physics.PhysicsObject;
import bridge.ui.Box2D;

/**
 * Driven wheel.
 */
public class Wheel extends PhysicsObject {

    public static final int CATEGORY = Car.CATEGORY;
    public static final int MASK = Car.MASK;

    public static final float RADIUS = 1f;
    private static final float MOTOR_SPEED = 10f;
    private static final float MOTOR_TORQUE = 80f;

    private Color fillColor = Color.decode("#555555");
    private Color spokeColor = Color.decode("#888888");
    private Color outlineColor = Color.BLACK;

    private RevoluteJoint axleJoint;
    private CircleShape shape;

    public Wheel(World world, Vec2 pos) {
        createPhysicsObject(world);
        setPos(pos);
    }

    @Override
    protected void createPhysicsObject(World world) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.DYNAMIC;
        body = world.createBody(bodyDef);

        shape = new CircleShape();
        shape.setRadius(RADIUS);

        FixtureDef fixtureDef = createFixtureDef(FRICTION, RESTITUTION, DENSITY, CATEGORY, MASK);
        fixtureDef.shape = shape;
        body.createFixture(fixtureDef);
    }

    public void attachToCar(World world, CarBody carBody) {
        RevoluteJointDef jointDef = new RevoluteJointDef();
        jointDef.initialize(body, carBody.getBody(), getPos());
        jointDef.motorSpeed = MOTOR_SPEED;
        jointDef.maxMotorTorque = MOTOR_TORQUE;
        jointDef.enableMotor = true;

        axleJoint = (RevoluteJoint) world.createJoint(jointDef);
    }

    public void draw(Graphics g, Box2D box2d) {
        int x = box2d.worldToPixelX(getX());
        int y = box2d.worldToPixelY(getY());
        int r = box2d.worldToPixel(RADIUS);

        g.setColor(fillColor);
        g.fillOval(x - r, y - r, r * 2, r * 2);

        g.setColor(spokeColor);
        int spokeCount = 5;
        for (int i = 0; i < spokeCount; i++) {
            double angle = getAngle() + 2 * Math.PI * i / (double) spokeCount;
            int x2 = box2d.worldToPixelX(getX() + RADIUS * (float) Math.cos(angle));
            int y2 = box2d.worldToPixelY(getY() + RADIUS * (float) Math.sin(angle));
            g.drawLine(x, y, x2, y2);
        }

        g.setColor(outlineColor);
        g.drawOval(x - r, y - r, r * 2, r * 2);
    }

    public void stopMotor() {
        axleJoint.setMotorSpeed(0);
    }

}
