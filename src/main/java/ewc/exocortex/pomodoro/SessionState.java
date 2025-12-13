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
    WAITING_FOR_USAGE_CONTEXT,
    WAITING_FOR_CONTEXT,
    WAITING_FOR_RESOURCES,
    WAITING_FOR_CONSTRAINTS,

    WORKING,

    // Post-work Reflection
    WAITING_FOR_ENERGY,
    WAITING_FOR_FOCUS,
    WAITING_FOR_QUALITY,
    WAITING_FOR_SUMMARY,
    WAITING_FOR_NEXT_STEP
}
