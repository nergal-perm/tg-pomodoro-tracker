package ewc.exocortex.pomodoro;

/**
 * Interface for managing session timers.
 */
public interface TimerService {

    /**
     * Creates a one-time schedule that will trigger after the specified duration.
     *
     * @param chatId  the chat ID to associate with the timer
     * @param minutes the duration in minutes
     * @return the schedule name (for cancellation)
     */
    String createTimer(long chatId, int minutes);

    /**
     * Cancels an existing timer.
     *
     * @param scheduleName the name of the schedule to cancel
     */
    void cancelTimer(String scheduleName);
}
