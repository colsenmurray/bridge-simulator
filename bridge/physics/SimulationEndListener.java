package bridge.physics;

/**
 * Called when a level play session ends (car stopped at goal or stuck).
 */
@FunctionalInterface
public interface SimulationEndListener {

    void onSessionEnd(boolean success, int price);
}
