package ewc.exocortex.pomodoro;

/**
 * Represents the possible states of a Pomodoro session.
 */
public enum SessionState {
    /**
     * No active session, waiting for /start command.
     */
    IDLE,
    WAITING_FOR_DURATION,

    // Pre-work Ritual
    WAITING_FOR_TASK,
    WAITING_FOR_ROLE,
    WAITING_FOR_PRODUCT_TYPE,
    WORKING,

    // Extension Choice
    WAITING_FOR_EXTENSION,

    // Post-work Reflection
    WAITING_FOR_OUTCOME
}
