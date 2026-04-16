package bridge.physics.nodes;

import java.awt.Color;

import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.World;

/**
 * Mobile (yellow) node created while building beams.
 */
public class MobileNode extends Node {

    public MobileNode(World world, Vec2 pos) {
        super(world, pos);

        fillColor = Color.decode("#e3f069");
        preview = true;
    }

    public void destroy(World world) {
        world.destroyBody(body);
    }

    @Override
    public void activatePhysics() {
        body.setType(BodyType.DYNAMIC);
        preview = false;
    }

    @Override
    protected void createPhysicsObject(World world) {
        createPhysicsObject(world, BodyType.KINEMATIC);
    }

}
