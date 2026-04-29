package bridge.physics;

/**
 * Called when a level play session ends (car stopped at goal, crashed, or similar).
 */
@FunctionalInterface
public interface SimulationEndListener {

    void onSessionEnd(boolean success, int price, SessionEndReason reason);
}
