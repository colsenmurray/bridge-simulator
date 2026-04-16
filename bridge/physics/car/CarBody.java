package bridge.physics.car;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.imageio.ImageIO;

import org.jbox2d.collision.shapes.PolygonShape;
import org.jbox2d.common.Vec2;
import org.jbox2d.dynamics.BodyDef;
import org.jbox2d.dynamics.BodyType;
import org.jbox2d.dynamics.FixtureDef;
import org.jbox2d.dynamics.World;

import bridge.physics.PhysicsObject;
import bridge.ui.Box2D;

/**
 * Car chassis / body.
 */
public class CarBody extends PhysicsObject {

    public static final int CATEGORY = Car.CATEGORY;
    public static final int MASK = Car.MASK;
    public static final Path IMAGE_PATH = Paths.get("res", "images");

    private BufferedImage image;
    private Color outlineColor = Color.BLACK;

    private PolygonShape shape;
    private float length = 8f;
    private float width = 3f;

    public CarBody(World world, Vec2 pos) {
        createPhysicsObject(world);
        setPos(pos);
        loadImage();
    }

    @Override
    protected void createPhysicsObject(World world) {
        BodyDef bodyDef = new BodyDef();
        bodyDef.type = BodyType.DYNAMIC;
        body = world.createBody(bodyDef);

        shape = new PolygonShape();
        shape.setAsBox(length / 2, width / 2);

        FixtureDef fixtureDef = createFixtureDef(FRICTION, RESTITUTION, DENSITY, CATEGORY, MASK);
        fixtureDef.shape = shape;
        body.createFixture(fixtureDef);
    }

    private void loadImage() {
        String path = IMAGE_PATH.resolve("voiture.png").toString();
        try {
            image = ImageIO.read(new File(path));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void draw(Graphics g, Box2D box2d) {
        double ratio = 1.2 * box2d.worldToPixel(length) / (double) image.getWidth();
        int drawWidth = (int) Math.ceil(image.getWidth() * ratio);
        int drawHeight = (int) Math.ceil(image.getHeight() * ratio);
        BufferedImage scaled = new BufferedImage(drawWidth, drawHeight, BufferedImage.TYPE_INT_ARGB);
        AffineTransform transform = new AffineTransform();
        transform.scale(ratio, ratio);
        AffineTransformOp transformOp = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);
        scaled = transformOp.filter(image, scaled);

        int x = box2d.worldToPixelX(getPos().x);
        int y = box2d.worldToPixelY(getPos().y);
        Graphics2D g2d = (Graphics2D) g;
        g2d.rotate(-getAngle(), x, y);
        g2d.drawImage(scaled, null, x - drawWidth / 2, y - drawHeight / 2);
        g2d.rotate(getAngle(), x, y);

        int[] xCorners = new int[4];
        int[] yCorners = new int[4];
        for (int i = 0; i < 4; i++) {
            Vec2 pos = shape.getVertex(i);

            float cos = (float) Math.cos(getAngle());
            float sin = (float) Math.sin(getAngle());
            float x2 = pos.x * cos - pos.y * sin + getX();
            float y2 = pos.x * sin + pos.y * cos + getY();
            xCorners[i] = box2d.worldToPixelX(x2);
            yCorners[i] = box2d.worldToPixelY(y2);
        }
        g.setColor(PhysicsObject.setColorAlpha(outlineColor, 0));
        g.drawPolygon(xCorners, yCorners, 4);
    }

}
