package ewc.exocortex.pomodoro;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory implementation of SessionRepository for testing.
 */
public class InMemorySessionRepository implements SessionRepository {

    private final Map<Long, SessionData> sessions = new HashMap<>();

    @Override
    public SessionData getSession(final long chatId) {
        return sessions.getOrDefault(chatId, SessionData.idle(chatId));
    }

    @Override
    public void saveSession(final SessionData session) {
        sessions.put(session.chatId(), session);
    }

    @Override
    public void deleteSession(final long chatId) {
        sessions.remove(chatId);
    }

    /**
     * For testing: check if a session exists.
     */
    public boolean hasSession(final long chatId) {
        return sessions.containsKey(chatId);
    }

    /**
     * For testing: clear all sessions.
     */
    public void clear() {
        sessions.clear();
    }
}
