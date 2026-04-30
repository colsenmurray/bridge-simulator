package bridge.physics;

import org.jbox2d.callbacks.ContactImpulse;
import org.jbox2d.callbacks.ContactListener;
import org.jbox2d.collision.Manifold;
import org.jbox2d.dynamics.contacts.Contact;
import org.jbox2d.dynamics.Fixture;

/**
 * Detects when the car hits river-bank terrain (crash) or the pit floor (fell).
 */
public class CarTerrainContactListener implements ContactListener {

    private final GameSession session;

    public CarTerrainContactListener(GameSession session) {
        this.session = session;
    }

    @Override
    public void beginContact(Contact contact) {
        if (session.isSessionFinished() || !session.isPhysicsRunning()) {
            return;
        }
        Fixture a = contact.getFixtureA();
        Fixture b = contact.getFixtureB();
        if (isCarAndPitFloor(a, b)) {
            session.markPendingPitFall();
        } else if (isChassisAndOrdinaryTerrain(a, b)) {
            session.markPendingTerrainCrash();
        }
    }

    private static boolean isCarAndPitFloor(Fixture a, Fixture b) {
        return (isCarPart(a) && isPitTerrain(b)) || (isCarPart(b) && isPitTerrain(a));
    }

    private static boolean isChassisAndOrdinaryTerrain(Fixture a, Fixture b) {
        return (isChassis(a) && isOrdinaryTerrain(b)) || (isChassis(b) && isOrdinaryTerrain(a));
    }

    private static boolean isCarPart(Fixture f) {
        return isChassis(f) || isWheel(f);
    }

    private static boolean isChassis(Fixture f) {
        return f.getUserData() == FixtureUserData.CAR_BODY;
    }

    private static boolean isWheel(Fixture f) {
        return f.getUserData() == FixtureUserData.CAR_WHEEL;
    }

    private static boolean isOrdinaryTerrain(Fixture f) {
        return f.getUserData() == FixtureUserData.TERRAIN;
    }

    private static boolean isPitTerrain(Fixture f) {
        return f.getUserData() == FixtureUserData.PIT_TERRAIN;
    }

    @Override
    public void endContact(Contact contact) {
    }

    @Override
    public void preSolve(Contact contact, Manifold oldManifold) {
    }

    @Override
    public void postSolve(Contact contact, ContactImpulse impulse) {
    }
}
