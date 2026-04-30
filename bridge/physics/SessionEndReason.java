package bridge.physics;

/**
 * Why a {@link GameSession} ended (or still running, for headless).
 */
public enum SessionEndReason {
    /** Physics still stepping (headless: hit max timesteps). */
    RUNNING,
    /** Car reached the finish window and session ended. */
    FINISH,
    /** Chassis collided with terrain (river bank / ground polyline), not the pit floor. */
    CRASH,
    /** Car body or wheel contacted the river pit floor. */
    FELL,
    /**
     * Car almost stationary for long enough (stuck) before the finish, so the sim does not run
     * forever.
     */
    STUCK,
    /** Headless: loop stopped by step limit, session not yet finished. */
    MAX_STEPS
}
