package bridge.physics.beams;

import java.awt.Color;

import org.jbox2d.collision.shapes.Shape;
import org.jbox2d.dynamics.FixtureDef;
import org.jbox2d.dynamics.World;

import bridge.physics.nodes.Node;

/**
 * Wood beam.
 */
public class WoodBeam extends Beam {

    public static final int CATEGORY = 0b00100000;
    public static final int MASK = Beam.MASK;

    public WoodBeam(World world, Node node1, Node node2) {
        super(world, node1, node2);

        fillColor = Color.decode("#ba754a");
        materialUnitPrice *= 1;
        maxForce *= 0.2f;
    }

    @Override
    protected FixtureDef createFixtureDef(Shape shape) {
        return createFixtureDef(FRICTION, RESTITUTION, DENSITY, CATEGORY, MASK);
    }

}
