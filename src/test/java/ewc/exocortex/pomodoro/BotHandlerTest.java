package ewc.exocortex.pomodoro;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private InMemorySessionRepository sessionRepository;
    private NoOpTimerService timerService;
    private FakeContext context;
    private BotHandler handler;

    @BeforeEach
    void setUp() {
        securityService = new SecurityService(ADMIN_ID);
        telegramApi = new FakeTelegramApi();
        driveApi = new FakeDriveApi();
        sessionRepository = new InMemorySessionRepository();
        timerService = new NoOpTimerService();
        context = new FakeContext();
        handler = new BotHandler(securityService, telegramApi, driveApi, sessionRepository, timerService);
    }

    @Nested
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("should ignore unauthorized users")
        void shouldIgnoreUnauthorizedUsers() {
            final var request = createMessageRequest(STRANGER_ID, "Hello");

            handler.handleRequest(request, context);

            assertTrue(telegramApi.sentMessages.isEmpty());
            assertTrue(telegramApi.keyboardMessages.isEmpty());
        }
    }

    @Nested
    @DisplayName("/start command")
    class StartCommand {

        @Test
        @DisplayName("should show duration selection keyboard")
        void shouldShowDurationKeyboard() {
            final var request = createMessageRequest(ADMIN_ID, "/start");

            handler.handleRequest(request, context);

            assertEquals(1, telegramApi.keyboardMessages.size());
            final var msg = telegramApi.keyboardMessages.get(0);
            assertTrue(msg.text().contains("How long"));
            assertEquals(5, msg.buttons().size());
        }

        @Test
        @DisplayName("should transition to WAITING_FOR_DURATION state")
        void shouldTransitionToWaitingForDuration() {
            final var request = createMessageRequest(ADMIN_ID, "/start");

            handler.handleRequest(request, context);

            final var session = sessionRepository.getSession(ADMIN_ID);
            assertEquals(SessionState.WAITING_FOR_DURATION, session.status());
        }
    }

    @Nested
    @DisplayName("Duration selection")
    class DurationSelection {

        @Test
        @DisplayName("should ask for title after duration selection")
        void shouldAskForTitleAfterDurationSelection() {
            handler.handleRequest(createMessageRequest(ADMIN_ID, "/start"), context);
            telegramApi.sentMessages.clear();

            final var request = createCallbackRequest(ADMIN_ID, "cb123", "duration:45");
            handler.handleRequest(request, context);

            assertEquals(1, telegramApi.sentMessages.size());
            assertTrue(telegramApi.sentMessages.get(0).text().contains("45 minutes"));
        }

        @Test
        @DisplayName("should transition to WAITING_FOR_TITLE state")
        void shouldTransitionToWaitingForTitle() {
            handler.handleRequest(createMessageRequest(ADMIN_ID, "/start"), context);
            handler.handleRequest(createCallbackRequest(ADMIN_ID, "cb123", "duration:30"), context);

            final var session = sessionRepository.getSession(ADMIN_ID);
            assertEquals(SessionState.WAITING_FOR_TITLE, session.status());
            assertEquals(30, session.duration());
        }
    }

    @Nested
    @DisplayName("Session lifecycle")
    class SessionLifecycle {

        @BeforeEach
        void startSession() {
            handler.handleRequest(createMessageRequest(ADMIN_ID, "/start"), context);
            handler.handleRequest(createCallbackRequest(ADMIN_ID, "cb123", "duration:45"), context);
            telegramApi.sentMessages.clear();
            telegramApi.keyboardMessages.clear();
        }

        @Test
        @DisplayName("should start timer after title input")
        void shouldStartTimerAfterTitle() {
            handler.handleRequest(createMessageRequest(ADMIN_ID, "My Task"), context);

            assertEquals(1, telegramApi.sentMessages.size());
            assertTrue(telegramApi.sentMessages.get(0).text().contains("Timer started"));
            assertTrue(telegramApi.sentMessages.get(0).text().contains("My Task"));

            final var session = sessionRepository.getSession(ADMIN_ID);
            assertEquals(SessionState.WORKING, session.status());
            assertEquals("My Task", session.sessionTitle());
            assertNotNull(timerService.getLastScheduleName());
        }

        @Test
        @DisplayName("should save to Drive after result input")
        void shouldSaveToDriveAfterResult() {
            handler.handleRequest(createMessageRequest(ADMIN_ID, "My Task"), context);
            telegramApi.sentMessages.clear();

            handler.handleRequest(createMessageRequest(ADMIN_ID, "/stop"), context);
            telegramApi.sentMessages.clear();

            handler.handleRequest(createMessageRequest(ADMIN_ID, "Completed the feature"), context);

            assertEquals(1, driveApi.uploadedNotes.size());
            assertTrue(driveApi.uploadedNotes.get(0).content().contains("Completed the feature"));
            assertTrue(driveApi.uploadedNotes.get(0).content().contains("timer_setting: 45"));
        }

        @Test
        @DisplayName("/stop should cancel timer")
        void shouldCancelTimerOnStop() {
            handler.handleRequest(createMessageRequest(ADMIN_ID, "My Task"), context);
            final String scheduleName = timerService.getLastScheduleName();

            handler.handleRequest(createMessageRequest(ADMIN_ID, "/stop"), context);

            assertEquals(scheduleName, timerService.getLastCancelledSchedule());
        }
    }

    @Nested
    @DisplayName("Timer events")
    class TimerEvents {

        @BeforeEach
        void startWorkingSession() {
            handler.handleRequest(createMessageRequest(ADMIN_ID, "/start"), context);
            handler.handleRequest(createCallbackRequest(ADMIN_ID, "cb123", "duration:45"), context);
            handler.handleRequest(createMessageRequest(ADMIN_ID, "My Task"), context);
            telegramApi.sentMessages.clear();
        }

        @Test
        @DisplayName("should prompt for result when timer fires")
        void shouldPromptForResultOnTimerDone() {
            final var timerEvent = createTimerDoneEvent(ADMIN_ID);

            handler.handleRequest(timerEvent, context);

            assertEquals(1, telegramApi.sentMessages.size());
            assertTrue(telegramApi.sentMessages.get(0).text().contains("Time's up"));

            final var session = sessionRepository.getSession(ADMIN_ID);
            assertEquals(SessionState.WAITING_FOR_RESULT, session.status());
        }

        @Test
        @DisplayName("should ignore timer for unauthorized user")
        void shouldIgnoreUnauthorizedTimer() {
            final var timerEvent = createTimerDoneEvent(STRANGER_ID);

            handler.handleRequest(timerEvent, context);

            assertTrue(telegramApi.sentMessages.isEmpty());
        }
    }

    @Nested
    @DisplayName("Empty/null handling")
    class EdgeCases {

        @Test
        @DisplayName("should handle empty body gracefully")
        void shouldHandleEmptyBodyGracefully() {
            final var request = Map.<String, Object>of("body", "");

            final var response = handler.handleRequest(request, context);

            assertEquals(200, response.get("statusCode"));
        }

        @Test
        @DisplayName("should handle null body gracefully")
        void shouldHandleNullBodyGracefully() {
            final var request = new HashMap<String, Object>();
            request.put("body", null);

            final var response = handler.handleRequest(request, context);

            assertEquals(200, response.get("statusCode"));
        }
    }

    // --- Test Helpers ---

    private Map<String, Object> createMessageRequest(final long chatId, final String text) {
        final String body = String.format("""
                {
                    "message": {
                        "chat": {"id": %d},
                        "text": "%s"
                    }
                }
                """, chatId, text);
        return Map.of("body", body);
    }

    private Map<String, Object> createCallbackRequest(final long chatId,
            final String callbackId,
            final String data) {
        final String body = String.format("""
                {
                    "callback_query": {
                        "id": "%s",
                        "message": {"chat": {"id": %d}},
                        "data": "%s"
                    }
                }
                """, callbackId, chatId, data);
        return Map.of("body", body);
    }

    private Map<String, Object> createTimerDoneEvent(final long chatId) {
        return Map.of(
                "action", "TIMER_DONE",
                "chatId", chatId);
    }

    // --- Test Doubles ---

    private static class FakeTelegramApi implements TelegramApi {
        final List<SentMessage> sentMessages = new ArrayList<>();
        final List<KeyboardMessage> keyboardMessages = new ArrayList<>();
        final List<String> answeredCallbacks = new ArrayList<>();
        private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

        @Override
        public Update parseUpdate(final String json) throws IOException {
            final var root = objectMapper.readTree(json);

            final var callbackQuery = root.get("callback_query");
            if (callbackQuery != null) {
                final long chatId = callbackQuery.get("message").get("chat").get("id").asLong();
                final String callbackQueryId = callbackQuery.get("id").asText();
                final String callbackData = callbackQuery.get("data").asText();
                return new Update(chatId, null, callbackQueryId, callbackData);
            }

            final var message = root.get("message");
            if (message == null) {
                return null;
            }
            final long chatId = message.get("chat").get("id").asLong();
            final String text = message.has("text") ? message.get("text").asText() : "";
            return new Update(chatId, text, null, null);
        }

        @Override
        public void sendMessage(final long chatId, final String text) {
            sentMessages.add(new SentMessage(chatId, text));
        }

        @Override
        public void sendMessageWithKeyboard(final long chatId, final String text, final List<Button> buttons) {
            keyboardMessages.add(new KeyboardMessage(chatId, text, buttons));
        }

        @Override
        public void answerCallbackQuery(final String callbackQueryId) {
            answeredCallbacks.add(callbackQueryId);
        }

        record SentMessage(long chatId, String text) {
        }

        record KeyboardMessage(long chatId, String text, List<Button> buttons) {
        }
    }

    private static class FakeDriveApi implements DriveApi {
        final List<UploadedNote> uploadedNotes = new ArrayList<>();

        @Override
        public String uploadNote(final String fileName, final String content) {
            uploadedNotes.add(new UploadedNote(fileName, content));
            return "fake-file-id";
        }

        record UploadedNote(String fileName, String content) {
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
