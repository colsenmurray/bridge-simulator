package bridge.physics.environment;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.LinkedList;

import org.jbox2d.collision.shapes.EdgeShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.Body;
import org.jbox2d.dynamics.BodyDef;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.FixtureDef;
import org.jbox2d.dynamics.World;

import bridge.level.Level;
import bridge.physics.PhysicsObject;
import bridge.physics.beams.Beam;
import bridge.physics.car.Car;
import bridge.physics.nodes.Node;
import bridge.ui.Box2D;

/**
 * Terrain polygon (river banks) for building the bridge.
 */
public class RiverBank extends PhysicsObject {

    public static final int CATEGORY = 0b0001;
    public static final int MASK = Car.CATEGORY | Beam.CATEGORY | Node.CATEGORY;

    private Color fillColor = Color.decode("#49a03f");
    private Color outlineColor = Color.BLACK;

    private LinkedList<Vec2> terrainPoints;

    public RiverBank(World world, Level level) {
        terrainPoints = level.getTerrainPoints();
        createPhysicsObject(world);
    }

    @Override
    protected void createPhysicsObject(World world) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.STATIC;

        FixtureDef fixtureDef = createFixtureDef(FRICTION, RESTITUTION, DENSITY, CATEGORY, MASK);

        Vec2 prev = null;
        for (Vec2 point : terrainPoints) {
            if (prev != null) {
                Body edgeBody = world.createBody(bodyDef);
                EdgeShape shape = new EdgeShape();
                shape.set(prev, point);
                fixtureDef.shape = shape;
                edgeBody.createFixture(fixtureDef);
            }
            prev = point;
        }
    }

    public boolean containsPoint(Vec2 pos) {
        Polygon polygon = new Polygon();
        for (Vec2 point : terrainPoints) {
            int x = Math.round(point.x * 1000);
            int y = Math.round(point.y * 1000);
            polygon.addPoint(x, y);
        }
        int x = Math.round(pos.x * 1000);
        int y = Math.round(pos.y * 1000);
        return polygon.contains(x, y);
    }

    public void draw(Graphics2D g, Box2D box2d) {
        Polygon polygon = new Polygon();
        for (Vec2 point : terrainPoints) {
            int x = box2d.worldToPixelX(point.x);
            int y = box2d.worldToPixelY(point.y);
            polygon.addPoint(x, y);
        }
        g.setColor(fillColor);
        g.fillPolygon(polygon);
        g.setColor(outlineColor);
        g.drawPolygon(polygon);
    }

}
