package ewc.exocortex.pomodoro;

/**
 * Represents the possible states of a Pomodoro session.
 */
public enum SessionState {
    /**
     * No active session, waiting for /start command.
     */
    IDLE,

    /**
     * Waiting for user to select session duration.
     */
    WAITING_FOR_DURATION,

    /**
     * Waiting for user to provide session title.
     */
    WAITING_FOR_TITLE,

    /**
     * Timer is running, user is working.
     */
    WORKING,

    /**
     * Session finished, waiting for user to provide result description.
     */
    WAITING_FOR_RESULT
}
