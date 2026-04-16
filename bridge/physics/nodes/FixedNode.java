package bridge.physics.nodes;

import java.awt.Color;

import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.World;

/**
 * Fixed (red) node that cannot move.
 */
public class FixedNode extends Node {

    public FixedNode(World world, Vec2 pos) {
        super(world, pos);

        fillColor = Color.decode("#d33d3d");
        preview = false;
    }

    @Override
    public void activatePhysics() {
    }

    @Override
    protected void createPhysicsObject(World world) {
        createPhysicsObject(world, BodyType.STATIC);
    }

}
