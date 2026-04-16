package bridge.physics.beams;

import java.awt.Color;

import org.jbox2d.collision.shapes.Shape;
import org.jbox2d.dynamics.FixtureDef;
import org.jbox2d.dynamics.World;

import bridge.physics.nodes.Node;

/**
 * Steel beam.
 */
public class SteelBeam extends Beam {

    public static final int CATEGORY = 0b01000000;
    public static final int MASK = Beam.MASK;

    public SteelBeam(World world, Node node1, Node node2) {
        super(world, node1, node2);

        fillColor = Color.decode("#8d92b2");
        materialUnitPrice *= 2;
        maxForce *= 1.5f;
    }

    @Override
    protected FixtureDef createFixtureDef(Shape shape) {
        return createFixtureDef(FRICTION, RESTITUTION, DENSITY, CATEGORY, MASK);
    }

}
