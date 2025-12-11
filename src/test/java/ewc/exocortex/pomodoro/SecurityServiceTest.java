package ewc.exocortex.pomodoro;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SecurityService.
 */
class SecurityServiceTest {

    private static final long ADMIN_ID = 123456789L;
    private static final long STRANGER_ID = 987654321L;

    @Nested
    @DisplayName("Authorization checks")
    class AuthorizationChecks {

        @Test
        @DisplayName("should allow admin user")
        void shouldAllowAdminUser() {
            final SecurityService service = new SecurityService(ADMIN_ID);
            assertTrue(service.isAuthorized(ADMIN_ID));
        }

        @Test
        @DisplayName("should deny unknown user")
        void shouldDenyUnknownUser() {
            final SecurityService service = new SecurityService(ADMIN_ID);
            assertFalse(service.isAuthorized(STRANGER_ID));
        }

        @Test
        @DisplayName("should deny zero chat ID")
        void shouldDenyZeroChatId() {
            final SecurityService service = new SecurityService(ADMIN_ID);
            assertFalse(service.isAuthorized(0L));
        }

        @Test
        @DisplayName("should handle negative chat IDs")
        void shouldHandleNegativeChatIds() {
            // Telegram group chats have negative IDs
            final long groupChatId = -100123456789L;
            final SecurityService service = new SecurityService(groupChatId);
            assertTrue(service.isAuthorized(groupChatId));
            assertFalse(service.isAuthorized(ADMIN_ID));
        }
    }
}
