package bridge.physics;

import java.awt.Color;

import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.FixtureDef;
import org.jbox2d.dynamics.World;

/**
 * Abstract base for physics-driven objects.
 */
public abstract class PhysicsObject {

    protected Body body;

    public static final float FRICTION = 0.8f;
    public static final float RESTITUTION = 0.3f;
    public static final float DENSITY = 1f;

    protected abstract void createPhysicsObject(World world);

    public Vec2 getPos() {
        return body.getTransform().p;
    }

    public float getX() {
        return body.getTransform().p.x;
    }

    public float getY() {
        return body.getTransform().p.y;
    }

    public float getAngle() {
        return body.getAngle();
    }

    public void setPos(Vec2 pos, float angle) {
        body.setTransform(pos.clone(), angle);
    }

    public void setPos(Vec2 pos) {
        setPos(pos, getAngle());
    }

    public Body getBody() {
        return body;
    }

    public static Color setColorAlpha(Color color, int alpha) {
        if (color.getAlpha() == alpha) {
            return color;
        }
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    public FixtureDef createFixtureDef(float friction, float restitution, float density, int category,
            int mask) {
        FixtureDef fixtureDef = new FixtureDef();
        fixtureDef.friction = friction;
        fixtureDef.restitution = restitution;
        fixtureDef.density = density;
        fixtureDef.filter.categoryBits = category;
        fixtureDef.filter.maskBits = mask;
        return fixtureDef;
    }

}
