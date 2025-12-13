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
 * Unit tests for BotHandler routing logic, updated for Ritual and Twist flows.
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

    @Test
    @DisplayName("Complete Ritual Flow")
    void shouldCompleteRitualFlow() {
        // 1. /start
        handler.handleRequest(createMessageRequest(ADMIN_ID, "/start"), context);
        assertLastMessageContains("Выберите продолжительность");
        assertState(SessionState.WAITING_FOR_DURATION);

        // 2. Select Duration
        handler.handleRequest(createCallbackRequest(ADMIN_ID, "cb1", "duration:45"), context);
        assertLastMessageContains("Что ты собираешься делать?");
        assertState(SessionState.WAITING_FOR_TASK);

        // 3. Enter Task
        handler.handleRequest(createMessageRequest(ADMIN_ID, "Coding"), context);
        assertLastMessageContains("В какой роли");
        assertState(SessionState.WAITING_FOR_ROLE);

        // 4. Select Role (Callback)
        handler.handleRequest(createCallbackRequest(ADMIN_ID, "cb2", "role:профессионал"), context);
        assertLastMessageContains("Какой рабочий продукт");
        assertState(SessionState.WAITING_FOR_PRODUCT_TYPE);

        // 5. Enter Product Type
        handler.handleRequest(createMessageRequest(ADMIN_ID, "Code"), context);
        assertLastMessageContains("Где и при каких условиях");
        assertState(SessionState.WAITING_FOR_USAGE_CONTEXT);

        // 6. Enter Usage Context
        handler.handleRequest(createMessageRequest(ADMIN_ID, "Production"), context);
        assertLastMessageContains("Каков контекст");
        assertState(SessionState.WAITING_FOR_CONTEXT);

        // 7. Enter Work Context
        handler.handleRequest(createMessageRequest(ADMIN_ID, "Urgent fix"), context);
        assertLastMessageContains("Каковы ресурсы");
        assertState(SessionState.WAITING_FOR_RESOURCES);

        // 8. Enter Resources
        handler.handleRequest(createMessageRequest(ADMIN_ID, "Coffee"), context);
        assertLastMessageContains("Ограничения");
        assertState(SessionState.WAITING_FOR_CONSTRAINTS);

        // 9. Enter Constraints -> START TIMER
        handler.handleRequest(createMessageRequest(ADMIN_ID, "None"), context);
        assertLastMessageContains("Таймер запущен");
        assertState(SessionState.WORKING);

        final var session = sessionRepository.getSession(ADMIN_ID);
        assertEquals(45, session.duration());
        assertEquals("Coding", session.task());
        assertNotNull(session.startTime());
        assertNotNull(session.scheduleName());
    }

    @Test
    @DisplayName("Complete Reflection Flow")
    void shouldCompleteReflectionFlow() {
        // Setup working session
        shouldCompleteRitualFlow();
        telegramApi.sentMessages.clear();

        // 1. Timer finishes (or Stop)
        handler.handleRequest(createMessageRequest(ADMIN_ID, "/stop"), context);
        assertLastMessageContains("уровень энергии");
        assertState(SessionState.WAITING_FOR_ENERGY);

        // 2. Select Energy
        handler.handleRequest(createCallbackRequest(ADMIN_ID, "cb3", "energy:5"), context);
        assertLastMessageContains("уровень фокуса");
        assertState(SessionState.WAITING_FOR_FOCUS);

        // 3. Select Focus
        handler.handleRequest(createCallbackRequest(ADMIN_ID, "cb4", "focus:3"), context);
        assertLastMessageContains("качество рабочего продукта");
        assertState(SessionState.WAITING_FOR_QUALITY);

        // 4. Select Quality
        handler.handleRequest(createCallbackRequest(ADMIN_ID, "cb5", "quality:3"), context);
        assertLastMessageContains("Подведите краткий итог");
        assertState(SessionState.WAITING_FOR_SUMMARY);

        // 5. Enter Summary
        handler.handleRequest(createMessageRequest(ADMIN_ID, "Did everything"), context);
        assertLastMessageContains("Каков следующий шаг");
        assertState(SessionState.WAITING_FOR_NEXT_STEP);

        // 6. Enter Next Step -> SAVE
        handler.handleRequest(createMessageRequest(ADMIN_ID, "Deep Work"), context);
        assertLastMessageContains("Сессия сохранена");

        // Session should be deleted/reset (IDLE is returned by repository for
        // non-existing)
        assertFalse(sessionRepository.hasSession(ADMIN_ID)); // Or check if IDLE

        assertEquals(1, driveApi.uploadedNotes.size());
        final String note = driveApi.uploadedNotes.get(0).content();
        System.out.println(note); // Debug
        assertTrue(note.contains("Coding"));
        assertTrue(note.contains("профессионал")); // Role
        assertTrue(note.contains("5")); // Energy short

        assertTrue(note.contains("Did everything"));
    }

    @Test
    @DisplayName("Timer Event triggers Reflection")
    void shouldTriggerReflectionOnTimer() {
        // Setup working session
        shouldCompleteRitualFlow();
        telegramApi.sentMessages.clear();

        // Timer Event
        final var timerEvent = Map.<String, Object>of("action", "TIMER_DONE", "chatId", ADMIN_ID);
        handler.handleRequest(timerEvent, context);

        assertLastMessageContains("Время вышло");
        assertState(SessionState.WAITING_FOR_ENERGY);
    }

    @Test
    @DisplayName("Unauthorized User Ignored")
    void shouldIgnoreUnauthorized() {
        handler.handleRequest(createMessageRequest(STRANGER_ID, "/start"), context);
        assertTrue(telegramApi.sentMessages.isEmpty());
    }

    // --- Helpers ---

    private void assertState(SessionState expected) {
        assertEquals(expected, sessionRepository.getSession(ADMIN_ID).status());
    }

    private void assertLastMessageContains(String substring) {
        assertFalse(telegramApi.allMessages.isEmpty(), "No messages sent");
        String lastText = telegramApi.allMessages.get(telegramApi.allMessages.size() - 1);
        assertTrue(lastText.contains(substring), "Last message '" + lastText + "' did not contain '" + substring + "'");
    }

    // --- Data Creation ---

    private Map<String, Object> createMessageRequest(final long chatId, final String text) {
        final String body = String.format("{\"message\":{\"chat\":{\"id\":%d},\"text\":\"%s\"}}", chatId, text);
        return Map.of("body", body);
    }

    private Map<String, Object> createCallbackRequest(final long chatId, final String id, final String data) {
        final String body = String.format(
                "{\"callback_query\":{\"id\":\"%s\",\"message\":{\"chat\":{\"id\":%d}},\"data\":\"%s\"}}", id, chatId,
                data);
        return Map.of("body", body);
    }

    // --- Fakes ---

    private static class FakeTelegramApi implements TelegramApi {
        final List<String> allMessages = new ArrayList<>(); // Stores text of all messages in order
        final List<SentMessage> sentMessages = new ArrayList<>();
        final List<KeyboardMessage> keyboardMessages = new ArrayList<>();

        @Override
        public Update parseUpdate(final String json) throws IOException {
            // Simple naive parser for test
            if (json.contains("callback_query")) {
                return new Update(ADMIN_ID, null, "cb1", json.split("\"data\":\"")[1].split("\"")[0]); // Hacky
            }
            if (json.contains("\"text\":\"")) {
                String text = json.split("\"text\":\"")[1].split("\"")[0];
                return new Update(ADMIN_ID, text, null, null);
            }
            return null;
        }

        @Override
        public void sendMessage(long chatId, String text) {
            sentMessages.add(new SentMessage(chatId, text));
            allMessages.add(text);
        }

        @Override
        public void sendMessageWithKeyboard(long chatId, String text, List<Button> buttons) {
            keyboardMessages.add(new KeyboardMessage(chatId, text, buttons));
            allMessages.add(text);
        }

        @Override
        public void answerCallbackQuery(String callbackQueryId) {
        }

        record SentMessage(long chatId, String text) {
        }

        record KeyboardMessage(long chatId, String text, List<Button> buttons) {
        }
    }

    private static class FakeDriveApi implements DriveApi {
        final List<UploadedNote> uploadedNotes = new ArrayList<>();

        @Override
        public String uploadNote(String fileName, String content) {
            uploadedNotes.add(new UploadedNote(fileName, content));
            return "id";
        }

        record UploadedNote(String fileName, String content) {
        }
    }

    private static class FakeContext implements Context {
        @Override
        public String getAwsRequestId() {
            return "req";
        }

        @Override
        public String getLogGroupName() {
            return "log";
        }

        @Override
        public String getLogStreamName() {
            return "stream";
        }

        @Override
        public String getFunctionName() {
            return "fn";
        }

        @Override
        public String getFunctionVersion() {
            return "v1";
        }

        @Override
        public String getInvokedFunctionArn() {
            return "arn";
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
            return 1000;
        }

        @Override
        public int getMemoryLimitInMB() {
            return 128;
        }

        @Override
        public LambdaLogger getLogger() {
            return new LambdaLogger() {
                @Override
                public void log(String message) {
                    System.out.println(message);
                }

                @Override
                public void log(byte[] message) {
                    System.out.println(new String(message));
                }
            };
        }
    }
}
