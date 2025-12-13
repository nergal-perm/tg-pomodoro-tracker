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
        String usageContext,
        String workContext,
        String resources,
        String constraints,

        Instant startTime, // When the timer started

        // Post-work Reflection Data
        String energyLevel,
        String focusLevel,
        String qualityLevel,
        String summary,
        String nextStep) {

    public static SessionData idle(final long chatId) {
        return new SessionData(chatId, SessionState.IDLE, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    public SessionData waitingForDuration() {
        return new SessionData(chatId, SessionState.WAITING_FOR_DURATION, null, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    public SessionData waitingForTask(final int selectedDuration) {
        return new SessionData(chatId, SessionState.WAITING_FOR_TASK, selectedDuration, null,
                null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    public SessionData waitingForRole(final String task) {
        return copyWithStatusAndRitual(SessionState.WAITING_FOR_ROLE, task, null, null, null, null, null, null);
    }

    public SessionData waitingForProductType(final String role) {
        return copyWithStatusAndRitual(SessionState.WAITING_FOR_PRODUCT_TYPE, task, role, null, null, null, null, null);
    }

    public SessionData waitingForUsageContext(final String productType) {
        return copyWithStatusAndRitual(SessionState.WAITING_FOR_USAGE_CONTEXT, task, role, productType, null, null,
                null, null);
    }

    public SessionData waitingForContext(final String usageContext) {
        return copyWithStatusAndRitual(SessionState.WAITING_FOR_CONTEXT, task, role, productType, usageContext, null,
                null, null);
    }

    public SessionData waitingForResources(final String workContext) {
        return copyWithStatusAndRitual(SessionState.WAITING_FOR_RESOURCES, task, role, productType, usageContext,
                workContext, null, null);
    }

    public SessionData waitingForConstraints(final String resources) {
        return copyWithStatusAndRitual(SessionState.WAITING_FOR_CONSTRAINTS, task, role, productType, usageContext,
                workContext, resources, null);
    }

    public SessionData working(final String constraints, final Instant start, final String schedule) {
        return new SessionData(chatId, SessionState.WORKING, duration, schedule,
                task, role, productType, usageContext, workContext, resources, constraints, start,
                null, null, null, null, null);
    }

    public SessionData waitingForEnergy() {
        return new SessionData(chatId, SessionState.WAITING_FOR_ENERGY, duration, scheduleName,
                task, role, productType, usageContext, workContext, resources, constraints, startTime,
                null, null, null, null, null);
    }

    public SessionData waitingForFocus(final String energyLevel) {
        return copyWithStatusAndReflection(SessionState.WAITING_FOR_FOCUS, energyLevel, null, null, null, null);
    }

    public SessionData waitingForQuality(final String focusLevel) {
        return copyWithStatusAndReflection(SessionState.WAITING_FOR_QUALITY, energyLevel, focusLevel, null, null, null);
    }

    public SessionData waitingForSummary(final String qualityLevel) {
        return copyWithStatusAndReflection(SessionState.WAITING_FOR_SUMMARY, energyLevel, focusLevel, qualityLevel,
                null, null);
    }

    public SessionData waitingForNextStep(final String summary) {
        return copyWithStatusAndReflection(SessionState.WAITING_FOR_NEXT_STEP, energyLevel, focusLevel, qualityLevel,
                summary, null);
    }

    private SessionData copyWithStatusAndRitual(SessionState pStatus, String pTask, String pRole, String pProd,
            String pUsage, String pCtx, String pRes, String pCons) {
        return new SessionData(chatId, pStatus, duration, scheduleName,
                pTask, pRole, pProd, pUsage, pCtx, pRes, pCons, startTime,
                energyLevel, focusLevel, qualityLevel, summary, nextStep);
    }

    private SessionData copyWithStatusAndReflection(SessionState pStatus, String pEnergy, String pFocus,
            String pQuality, String pSum, String pNext) {
        return new SessionData(chatId, pStatus, duration, scheduleName,
                task, role, productType, usageContext, workContext, resources, constraints, startTime,
                pEnergy, pFocus, pQuality, pSum, pNext);
    }
}
