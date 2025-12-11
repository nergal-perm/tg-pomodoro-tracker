package ewc.exocortex.pomodoro;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for BotHandler routing logic.
 * Uses simple test doubles instead of mocking frameworks.
 */
class BotHandlerTest {

    private static final long ADMIN_ID = 123456789L;
    private static final long STRANGER_ID = 987654321L;

    private SecurityService securityService;
    private FakeTelegramApi telegramApi;
    private FakeDriveApi driveApi;
    private FakeContext context;
    private BotHandler handler;

    @BeforeEach
    void setUp() {
        securityService = new SecurityService(ADMIN_ID);
        telegramApi = new FakeTelegramApi();
        driveApi = new FakeDriveApi();
        context = new FakeContext();
        handler = new BotHandler(securityService, telegramApi, driveApi);
    }

    @Nested
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("should ignore unauthorized users")
        void shouldIgnoreUnauthorizedUsers() {
            final var request = createRequest(STRANGER_ID, "Hello");

            final var response = handler.handleRequest(request, context);

            assertEquals(200, response.getStatusCode());
            assertTrue(telegramApi.sentMessages.isEmpty());
            assertTrue(driveApi.uploadedNotes.isEmpty());
        }
    }

    @Nested
    @DisplayName("Command routing")
    class CommandRouting {

        @Test
        @DisplayName("should respond to /start command")
        void shouldRespondToStartCommand() {
            final var request = createRequest(ADMIN_ID, "/start");

            handler.handleRequest(request, context);

            assertEquals(1, telegramApi.sentMessages.size());
            assertTrue(telegramApi.sentMessages.get(0).text().contains("ready"));
        }

        @Test
        @DisplayName("should save text message to Drive")
        void shouldSaveTextMessageToDrive() {
            final var request = createRequest(ADMIN_ID, "My work log entry");

            handler.handleRequest(request, context);

            assertEquals(1, driveApi.uploadedNotes.size());
            assertEquals("My work log entry", driveApi.uploadedNotes.get(0));
            assertEquals(1, telegramApi.sentMessages.size());
            assertTrue(telegramApi.sentMessages.get(0).text().contains("Saved"));
        }

        @Test
        @DisplayName("should handle empty body gracefully")
        void shouldHandleEmptyBodyGracefully() {
            final var request = new APIGatewayProxyRequestEvent();
            request.setBody("");

            final var response = handler.handleRequest(request, context);

            assertEquals(200, response.getStatusCode());
        }
    }

    // --- Test Doubles ---

    private APIGatewayProxyRequestEvent createRequest(final long chatId, final String text) {
        final var request = new APIGatewayProxyRequestEvent();
        request.setBody(String.format("""
                {
                    "message": {
                        "chat": {"id": %d},
                        "text": "%s"
                    }
                }
                """, chatId, text));
        return request;
    }

    private static class FakeTelegramApi implements TelegramApi {
        final List<SentMessage> sentMessages = new ArrayList<>();
        private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        @Override
        public Update parseUpdate(final String json) throws IOException {
            final var root = objectMapper.readTree(json);
            final var message = root.get("message");
            if (message == null) {
                return null;
            }
            final long chatId = message.get("chat").get("id").asLong();
            final String text = message.has("text") ? message.get("text").asText() : "";
            return new Update(chatId, text);
        }

        @Override
        public void sendMessage(final long chatId, final String text) {
            sentMessages.add(new SentMessage(chatId, text));
        }

        record SentMessage(long chatId, String text) {
        }
    }

    private static class FakeDriveApi implements DriveApi {
        final List<String> uploadedNotes = new ArrayList<>();

        @Override
        public String uploadNote(final String content) {
            uploadedNotes.add(content);
            return "fake-file-id";
        }
    }

    private static class FakeContext implements Context {
        private final List<String> logs = new ArrayList<>();

        @Override
        public String getAwsRequestId() {
            return "test-request-id";
        }

        @Override
        public String getLogGroupName() {
            return "test-log-group";
        }

        @Override
        public String getLogStreamName() {
            return "test-log-stream";
        }

        @Override
        public String getFunctionName() {
            return "BotHandler";
        }

        @Override
        public String getFunctionVersion() {
            return "1";
        }

        @Override
        public String getInvokedFunctionArn() {
            return "arn:aws:lambda:test";
        }

        @Override
        public com.amazonaws.services.lambda.runtime.CognitoIdentity getIdentity() {
            return null;
        }

        @Override
        public com.amazonaws.services.lambda.runtime.ClientContext getClientContext() {
            return null;
        }

        @Override
        public int getRemainingTimeInMillis() {
            return 30000;
        }

        @Override
        public int getMemoryLimitInMB() {
            return 512;
        }

        @Override
        public LambdaLogger getLogger() {
            return new LambdaLogger() {
                @Override
                public void log(String message) {
                    logs.add(message);
                }

                @Override
                public void log(byte[] message) {
                    logs.add(new String(message));
                }
            };
        }
    }
}
