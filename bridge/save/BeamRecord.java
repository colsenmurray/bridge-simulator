package bridge.save;

import org.jbox2d.common.Vec2;

import bridge.physics.beams.Material;

/**
 * One beam in a saved bridge file.
 */
public class BeamRecord {

    private final Material material;
    private final Vec2 a;
    private final Vec2 b;

    public BeamRecord(Material material, Vec2 a, Vec2 b) {
        this.material = material;
        this.a = a.clone();
        this.b = b.clone();
    }

    public Material getMaterial() {
        return material;
    }

    public Vec2 getA() {
        return a;
    }

    public Vec2 getB() {
        return b;
    }

}
