package ewc.exocortex.pomodoro;

import java.time.Instant;

/**
 * Represents the persisted state of a user's Pomodoro session.
 * This is stored in DynamoDB to maintain state across Lambda invocations.
 *
 * @param chatId       The Telegram chat ID (partition key)
 * @param status       Current state of the session
 * @param sessionTitle User-provided title for the work session
 * @param duration     Selected duration in minutes
 * @param startTime    When the working session started (ISO8601)
 * @param scheduleName The EventBridge schedule name for cancellation
 */
public record SessionData(
        long chatId,
        SessionState status,
        String sessionTitle,
        Integer duration,
        Instant startTime,
        String scheduleName) {
    /**
     * Creates a new session in IDLE state.
     */
    public static SessionData idle(final long chatId) {
        return new SessionData(chatId, SessionState.IDLE, null, null, null, null);
    }

    /**
     * Transitions to WAITING_FOR_DURATION state.
     */
    public SessionData waitingForDuration() {
        return new SessionData(chatId, SessionState.WAITING_FOR_DURATION, null, null, null, null);
    }

    /**
     * Transitions to WAITING_FOR_TITLE state with selected duration.
     */
    public SessionData waitingForTitle(final int selectedDuration) {
        return new SessionData(chatId, SessionState.WAITING_FOR_TITLE, null, selectedDuration, null, null);
    }

    /**
     * Transitions to WORKING state with title and start time.
     */
    public SessionData working(final String title, final Instant start, final String schedule) {
        return new SessionData(chatId, SessionState.WORKING, title, duration, start, schedule);
    }

    /**
     * Transitions to WAITING_FOR_RESULT state.
     */
    public SessionData waitingForResult() {
        return new SessionData(chatId, SessionState.WAITING_FOR_RESULT, sessionTitle, duration, startTime, null);
    }
}
