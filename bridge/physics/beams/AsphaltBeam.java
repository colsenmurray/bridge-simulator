package bridge.physics.beams;

import java.awt.Color;

import org.jbox2d.collision.shapes.Shape;
import org.jbox2d.dynamics.FixtureDef;
import org.jbox2d.dynamics.World;

import bridge.physics.car.Car;
import bridge.physics.nodes.Node;

/**
 * Asphalt beam (drivable surface).
 */
public class AsphaltBeam extends Beam {

    public static final int CATEGORY = 0b00010000;
    public static final int MASK = Beam.MASK | Car.CATEGORY;

    public AsphaltBeam(World world, Node node1, Node node2) {
        super(world, node1, node2);

        fillColor = Color.decode("#333333");
        materialUnitPrice *= 3;
        maxForce *= 1;
    }

    @Override
    protected FixtureDef createFixtureDef(Shape shape) {
        return createFixtureDef(FRICTION, RESTITUTION, DENSITY, CATEGORY, MASK);
    }

}
