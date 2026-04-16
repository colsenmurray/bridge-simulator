package bridge.physics.beams;

import java.awt.Color;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.LinkedList;

import org.jbox2d.collision.shapes.PolygonShape;
import org.jbox2d.collision.shapes.Shape;
import org.jbox2d.common.Rot;
import org.jbox2d.common.Transform;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.BodyDef;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.Filter;
import org.jbox2d.dynamics.Fixture;
import org.jbox2d.dynamics.FixtureDef;
import org.jbox2d.dynamics.World;
import org.jbox2d.dynamics.joints.RevoluteJoint;
import org.jbox2d.dynamics.joints.RevoluteJointDef;

import bridge.physics.PhysicsObject;
import bridge.physics.car.Car;
import bridge.physics.environment.RiverBank;
import bridge.physics.nodes.FixedNode;
import bridge.physics.nodes.MobileNode;
import bridge.physics.nodes.Node;

/**
 * Abstract beam between two nodes.
 */
public abstract class Beam extends PhysicsObject {

    public static final int CATEGORY = 0b1000;
    public static final int MASK = Car.CATEGORY;

    private static final float MAX_LENGTH = 8;
    private static final float MIN_LENGTH = 0;

    private ArrayList<Node> linkedNodes;
    private ArrayList<RevoluteJoint> revoluteJoints;
    private PolygonShape shape;
    private FixtureDef fixtureDef;
    private Fixture fixture;

    protected Color fillColor;
    private Color outlineColor = Color.BLACK;
    private boolean preview = true;

    private float length;
    private static final float WIDTH = 1;
    private int price;

    protected float restitution = PhysicsObject.RESTITUTION;
    protected float maxForce = 500f;
    protected int materialUnitPrice = 1000;

    protected Beam(World world, Node node1, Node node2) {
        linkedNodes = new ArrayList<Node>(2);
        revoluteJoints = new ArrayList<RevoluteJoint>(2);
        addNode(node1);
        addNode(node2);

        createPhysicsObject(world);
        adjustPosition();
    }

    public int getPrice() {
        return price;
    }

    @Override
    protected void createPhysicsObject(World world) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.KINEMATIC;

        body = world.createBody(bodyDef);

        shape = new PolygonShape();

        fixtureDef = createFixtureDef(shape);
        fixtureDef.shape = shape;
        fixtureDef.filter.categoryBits |= Beam.CATEGORY;
    }

    protected abstract FixtureDef createFixtureDef(Shape shape);

    public void addNode(Node node) {
        linkedNodes.add(node);
        node.getLinkedBeams().add(this);
    }

    public void connect(World world, Node node) {
        RevoluteJointDef jointDef = new RevoluteJointDef();
        jointDef.initialize(body, node.getBody(), node.getPos());
        jointDef.collideConnected = true;
        RevoluteJoint joint = (RevoluteJoint) world.createJoint(jointDef);

        revoluteJoints.add(joint);
    }

    public LinkedList<Node> testBreak(World world, float dt) {
        LinkedList<Node> newNodes = new LinkedList<>();

        for (int i = 0; i < revoluteJoints.size(); i++) {
            RevoluteJoint joint = revoluteJoints.get(i);
            Node node = linkedNodes.get(i);

            if (node.getLinkedBeams().size() > 1) {

                if (computeBreak(joint, node, dt)) {
                    removeNodeAt(world, i);

                    MobileNode newNode = new MobileNode(world, node.getPos());
                    addNode(newNode);
                    newNode.activatePhysics();
                    connect(world, newNode);
                    newNodes.add(newNode);
                }
            }
        }
        addRiverBankCollision();
        return newNodes;
    }

    public boolean computeBreak(RevoluteJoint joint, Node node, float dt) {
        Vec2 force = new Vec2();
        joint.getReactionForce(1 / dt, force);

        Vec2 beamVec = linkedNodes.get(0).getPos().sub(linkedNodes.get(1).getPos());
        Vec2 orthogonal = new Vec2(-beamVec.y, beamVec.x);
        orthogonal.normalize();
        float projection = Math.abs(Vec2.dot(orthogonal, force));

        float value = projection / (node.getLinkedBeams().size() - 1);
        if (node instanceof FixedNode) {
            value /= 20;
        }

        return value > maxForce;
    }

    public void removeNodeAt(World world, int index) {
        if (!revoluteJoints.isEmpty()) {
            RevoluteJoint joint = revoluteJoints.get(index);
            world.destroyJoint(joint);
            revoluteJoints.remove(index);
        }

        Node node = linkedNodes.get(index);
        node.getLinkedBeams().remove(this);
        linkedNodes.remove(index);
    }

    public void addRiverBankCollision() {
        boolean fixed = false;
        for (Node node : linkedNodes) {
            if (node instanceof FixedNode) {
                fixed = true;
            }
        }
        if (!fixed) {
            Filter filter = fixture.getFilterData();
            filter.maskBits |= RiverBank.CATEGORY;
            fixture.setFilterData(filter);
        }
    }

    public void draw(Graphics g, bridge.ui.Box2D box2d) {
        int[] xCorners = new int[4];
        int[] yCorners = new int[4];

        for (int i = 0; i < 4; i++) {
            Vec2 pos = shape.getVertex(i);

            float cos = (float) Math.cos(getAngle());
            float sin = (float) Math.sin(getAngle());
            float x = pos.x * cos - pos.y * sin + getX();
            float y = pos.x * sin + pos.y * cos + getY();

            xCorners[i] = box2d.worldToPixelX(x);
            yCorners[i] = box2d.worldToPixelY(y);
        }

        int alpha = preview ? 100 : 255;
        fillColor = PhysicsObject.setColorAlpha(fillColor, alpha);
        outlineColor = PhysicsObject.setColorAlpha(outlineColor, alpha);

        g.setColor(fillColor);
        g.fillPolygon(xCorners, yCorners, 4);
        g.setColor(outlineColor);
        g.drawPolygon(xCorners, yCorners, 4);
    }

    public boolean testBeamClicked(Vec2 clickPos) {
        Transform transform = new Transform(getPos(), new Rot(getAngle()));
        return shape.testPoint(transform, clickPos);
    }

    public LinkedList<MobileNode> removeFromWorld(World world) {
        LinkedList<MobileNode> removeNodes = new LinkedList<>();
        for (Node node : linkedNodes) {
            node.getLinkedBeams().remove(this);
            if (node instanceof MobileNode && node.getLinkedBeams().isEmpty()) {
                MobileNode toRemove = (MobileNode) node;
                removeNodes.add(toRemove);
                toRemove.destroy(world);
            }
        }
        world.destroyBody(this.body);
        return removeNodes;
    }

    public void adjustPosition() {
        Node node1 = linkedNodes.get(0);
        Node node2 = linkedNodes.get(1);
        Vec2 center = node1.getPos().add(node2.getPos()).mul(0.5f);
        Vec2 difference = node1.getPos().sub(node2.getPos());
        float angle = (float) Math.atan(difference.y / difference.x);
        length = difference.length();

        shape.setAsBox(length / 2, WIDTH / 2);
        setPos(center, angle);

        if (fixture != null) {
            body.destroyFixture(fixture);
        }
        fixtureDef.shape = shape;
        fixture = body.createFixture(fixtureDef);
    }

    public void activatePhysics() {
        body.setType(BodyType.DYNAMIC);
        preview = false;
        price = Math.round(length / MAX_LENGTH * materialUnitPrice);
        addRiverBankCollision();
        adjustPosition();
    }

    public void attachTo(World world, Node clickedNode) {
        removeNodeAt(world, 1);
        addNode(clickedNode);
    }

    public boolean hasMinimumLength() {
        return length > MIN_LENGTH;
    }

    public ArrayList<Node> getLinkedNodes() {
        return linkedNodes;
    }

    public boolean isWithinMaxLength(Vec2 pos) {
        Node node1 = linkedNodes.get(0);
        float newLength = node1.getPos().sub(pos).length();
        return newLength <= MAX_LENGTH;
    }

    public Vec2 clampedEndPosition(Vec2 pos) {
        Node node1 = linkedNodes.get(0);
        Vec2 vector = pos.sub(node1.getPos());
        vector.normalize();
        return node1.getPos().add(vector.mul(MAX_LENGTH));
    }

}
