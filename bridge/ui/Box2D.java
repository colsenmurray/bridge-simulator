package bridge.ui;

import org.jbox2d.common.Vec2;

/**
 * Maps between pixel coordinates and world (meter) coordinates.
 */
public class Box2D {

    private MainFrame mainFrame;
    /** Used when {@link #mainFrame} is null (headless). */
    private int headlessPixelWidth;
    private int headlessPixelHeight;

    private float worldWidth;
    private float worldHeight;

    public Box2D(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        this.headlessPixelWidth = -1;
        this.headlessPixelHeight = -1;

        worldWidth = 100f;
        worldHeight = getPixelHeight() * metersPerPixel();
    }

    /**
     * Fixed pixel size (e.g. for headless runs) with the same world width as the GUI {@link Box2D}.
     */
    public Box2D(int pixelWidth, int pixelHeight) {
        this.mainFrame = null;
        this.headlessPixelWidth = pixelWidth;
        this.headlessPixelHeight = pixelHeight;

        worldWidth = 100f;
        worldHeight = getPixelHeight() * metersPerPixel();
    }

    private float metersPerPixel() {
        return worldWidth / getPixelWidth();
    }

    public float pixelToWorld(int pixels) {
        return metersPerPixel() * pixels;
    }

    public float pixelToWorldX(int xP) {
        return pixelToWorld(xP);
    }

    public float pixelToWorldY(int yP) {
        return pixelToWorld(getPixelHeight() - yP);
    }

    public Vec2 pixelToWorld(int xP, int yP) {
        return new Vec2(pixelToWorld(xP), pixelToWorldY(yP));
    }

    public int worldToPixel(float worldDistance) {
        return Math.round(worldDistance / metersPerPixel());
    }

    public int worldToPixelX(float x) {
        return worldToPixel(x);
    }

    public int worldToPixelY(float y) {
        return getPixelHeight() - worldToPixel(y);
    }

    public float getWorldWidth() {
        return worldWidth;
    }

    public float getWorldHeight() {
        return worldHeight;
    }

    public int getPixelWidth() {
        return mainFrame != null ? mainFrame.getWidth() : headlessPixelWidth;
    }

    public int getPixelHeight() {
        return mainFrame != null ? mainFrame.getHeight() : headlessPixelHeight;
    }

}
