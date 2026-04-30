package bridge.physics;

/**
 * Mark Box2D fixtures for contact routing (use reference identity, not value).
 */
public final class FixtureUserData {

    public static final Object TERRAIN = new Object();
    /** River pit floor: contact ends session as FELL, not a side-bank crash. */
    public static final Object PIT_TERRAIN = new Object();
    public static final Object CAR_BODY = new Object();
    public static final Object CAR_WHEEL = new Object();

    private FixtureUserData() {
    }
}
