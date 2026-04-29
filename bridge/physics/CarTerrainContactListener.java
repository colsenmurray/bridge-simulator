package bridge.physics;

import org.jbox2d.callbacks.ContactImpulse;
import org.jbox2d.callbacks.ContactListener;
import org.jbox2d.collision.Manifold;
import org.jbox2d.dynamics.contacts.Contact;
import org.jbox2d.dynamics.Fixture;

/**
 * Detects when the car chassis (not just wheels) hits the river-bank / terrain polyline.
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
        if (isChassisAndTerrain(a, b)) {
            session.markPendingTerrainCrash();
        }
    }

    private static boolean isChassisAndTerrain(Fixture a, Fixture b) {
        return (isChassis(a) && isTerrain(b)) || (isChassis(b) && isTerrain(a));
    }

    private static boolean isChassis(Fixture f) {
        return f.getUserData() == FixtureUserData.CAR_BODY;
    }

    private static boolean isTerrain(Fixture f) {
        return f.getUserData() == FixtureUserData.TERRAIN;
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
