package ewc.exocortex.pomodoro;

/**
 * Interface for session state persistence operations.
 */
public interface SessionRepository {

    /**
     * Retrieves the current session for a chat.
     * Returns IDLE state if no session exists.
     *
     * @param chatId the Telegram chat ID
     * @return the session data, never null
     */
    SessionData getSession(long chatId);

    /**
     * Saves or updates a session.
     *
     * @param session the session data to save
     */
    void saveSession(SessionData session);

    /**
     * Deletes a session (clears state).
     *
     * @param chatId the Telegram chat ID
     */
    void deleteSession(long chatId);
}
