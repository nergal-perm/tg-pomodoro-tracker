package ewc.exocortex.pomodoro;

import java.time.Instant;

/**
 * Represents the persisted state of a user's Pomodoro session.
 * This is stored in DynamoDB to maintain state across Lambda invocations.
 */
public record SessionData(
        long chatId,
        SessionState status,
        Integer duration, // Selected duration in minutes
        String scheduleName, // EventBridge schedule name

        // Pre-work Ritual Data
        String task,
        String role,
        String productType,

        Instant startTime, // When the timer started

        // Post-work Reflection Data
        String outcome // What was done + reflection + next step
) {

    public static SessionData idle(final long chatId) {
        return new SessionData(chatId, SessionState.IDLE, null, null,
                null, null, null, null,
                null);
    }

    public SessionData waitingForDuration() {
        return new SessionData(chatId, SessionState.WAITING_FOR_DURATION, null, null,
                null, null, null, null,
                null);
    }

    public SessionData waitingForTask(final int selectedDuration) {
        return new SessionData(chatId, SessionState.WAITING_FOR_TASK, selectedDuration, null,
                null, null, null, null,
                null);
    }

    public SessionData waitingForRole(final String task) {
        return copyWithStatusAndRitual(SessionState.WAITING_FOR_ROLE, task, null, null);
    }

    public SessionData waitingForProductType(final String role) {
        return copyWithStatusAndRitual(SessionState.WAITING_FOR_PRODUCT_TYPE, task, role, null);
    }

    public SessionData working(final String productType, final Instant start, final String schedule) {
        return new SessionData(chatId, SessionState.WORKING, duration, schedule,
                task, role, productType, start,
                null);
    }

    public SessionData waitingForExtension() {
        return new SessionData(chatId, SessionState.WAITING_FOR_EXTENSION, duration, scheduleName,
                task, role, productType, startTime,
                null);
    }

    public SessionData workingExtended(final String newScheduleName) {
        return new SessionData(chatId, SessionState.WORKING, duration, newScheduleName,
                task, role, productType, startTime,
                null);
    }

    public SessionData waitingForOutcome() {
        return new SessionData(chatId, SessionState.WAITING_FOR_OUTCOME, duration, scheduleName,
                task, role, productType, startTime,
                null);
    }

    private SessionData copyWithStatusAndRitual(SessionState pStatus, String pTask, String pRole, String pProd) {
        return new SessionData(chatId, pStatus, duration, scheduleName,
                pTask, pRole, pProd, startTime,
                outcome);
    }
}
