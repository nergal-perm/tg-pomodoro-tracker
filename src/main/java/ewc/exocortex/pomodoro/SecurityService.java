package ewc.exocortex.pomodoro;

/**
 * Verifies that the incoming Telegram message is from the authorized user.
 * This is a single-user bot, so we hardcode the allowed Chat ID.
 */
public final class SecurityService {

    private final long adminChatId;

    public SecurityService(final long adminChatId) {
        this.adminChatId = adminChatId;
    }

    /**
     * Creates a SecurityService using the ADMIN_CHAT_ID environment variable.
     */
    public static SecurityService fromEnvironment() {
        final String envValue = System.getenv("ADMIN_CHAT_ID");
        if (envValue == null || envValue.isBlank()) {
            throw new IllegalStateException("ADMIN_CHAT_ID environment variable is not set");
        }
        return new SecurityService(Long.parseLong(envValue));
    }

    /**
     * Checks if the given Chat ID is authorized.
     *
     * @param chatId the Chat ID from the incoming Telegram message
     * @return true if authorized, false otherwise
     */
    public boolean isAuthorized(final long chatId) {
        return chatId == adminChatId;
    }
}
