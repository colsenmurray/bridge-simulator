package bridge.physics.nodes;

import java.awt.Color;
import java.awt.Graphics;
import java.util.LinkedList;

import org.jbox2d.collision.shapes.CircleShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.BodyDef;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.FixtureDef;
import org.jbox2d.dynamics.World;

import bridge.physics.PhysicsObject;
import bridge.physics.beams.Beam;
import bridge.physics.environment.RiverBank;

/**
 * Graph node (joint anchor) for the bridge editor.
 */
public abstract class Node extends PhysicsObject {

    public static final int CATEGORY = 0b0100;
    public static final int MASK = RiverBank.CATEGORY;

    public static final float RADIUS = 0.5f;
    private static final float CLICK_RADIUS = RADIUS * 3;

    protected Color fillColor;
    private Color hoverColor;
    private Color outlineColor;

    private LinkedList<Beam> linkedBeams;
    protected boolean preview;

    protected Node(World world, Vec2 pos) {
        outlineColor = Color.BLACK;
        hoverColor = Color.decode("#e86933");

        linkedBeams = new LinkedList<Beam>();

        createPhysicsObject(world);
        setPos(pos);
    }

    protected void createPhysicsObject(World world, BodyType bodyType) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = bodyType;

        body = world.createBody(bodyDef);
        CircleShape shape = new CircleShape();
        shape.setRadius(RADIUS);

        FixtureDef fixtureDef = createFixtureDef(FRICTION, RESTITUTION, DENSITY, CATEGORY, MASK);
        fixtureDef.shape = shape;
        body.createFixture(fixtureDef);
    }

    public abstract void activatePhysics();

    public void draw(Graphics g, bridge.ui.Box2D box2d, boolean hovered) {
        int x = box2d.worldToPixelX(getX());
        int y = box2d.worldToPixelY(getY());
        int r = box2d.worldToPixel(RADIUS);

        int alpha = preview ? 100 : 255;
        fillColor = PhysicsObject.setColorAlpha(fillColor, alpha);
        outlineColor = PhysicsObject.setColorAlpha(outlineColor, alpha);
        hoverColor = PhysicsObject.setColorAlpha(hoverColor, alpha);

        g.setColor(hovered ? hoverColor : fillColor);
        g.fillOval(x - r, y - r, r * 2, r * 2);
        g.setColor(outlineColor);
        g.drawOval(x - r, y - r, r * 2, r * 2);
    }

    public float distanceToPoint(Vec2 pos) {
        Vec2 center = getPos();
        return center.sub(pos).length();
    }

    public boolean testNodeClicked(Vec2 pos) {
        return distanceToPoint(pos) <= CLICK_RADIUS;
    }

    public LinkedList<Beam> getLinkedBeams() {
        return linkedBeams;
    }

}
