package ewc.exocortex.pomodoro;

/**
 * No-op implementation of TimerService for testing.
 */
public class NoOpTimerService implements TimerService {

    private String lastScheduleName;
    private String lastCancelledSchedule;

    @Override
    public String createTimer(final long chatId, final int minutes) {
        lastScheduleName = "test-schedule-" + chatId + "-" + minutes;
        return lastScheduleName;
    }

    @Override
    public void cancelTimer(final String scheduleName) {
        lastCancelledSchedule = scheduleName;
    }

    public String getLastScheduleName() {
        return lastScheduleName;
    }

    public String getLastCancelledSchedule() {
        return lastCancelledSchedule;
    }
}
