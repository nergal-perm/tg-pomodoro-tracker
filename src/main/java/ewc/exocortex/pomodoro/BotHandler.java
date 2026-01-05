package ewc.exocortex.pomodoro;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Main Lambda handler for the Pomodoro Bot.
 */
public class BotHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final List<TelegramApi.Button> DURATION_BUTTONS = List.of(
            new TelegramApi.Button("5 минут", "duration:5"),
            new TelegramApi.Button("30 минут", "duration:30"),
            new TelegramApi.Button("45 минут", "duration:45"),
            new TelegramApi.Button("60 минут", "duration:60"),
            new TelegramApi.Button("90 минут", "duration:90"));

    private static final List<TelegramApi.Button> ROLE_BUTTONS = List.of(
            new TelegramApi.Button("Ученик", "role:ученик"),
            new TelegramApi.Button("Интеллектуал", "role:интеллектуал"),
            new TelegramApi.Button("Профессионал", "role:профессионал"),
            new TelegramApi.Button("Исследователь", "role:исследователь"),
            new TelegramApi.Button("Просветитель", "role:просветитель"));

    private static final List<TelegramApi.Button> EXTENSION_BUTTONS = List.of(
            new TelegramApi.Button("Завершить", "extension:finish"),
            new TelegramApi.Button("+5 мин", "extension:5"),
            new TelegramApi.Button("+10 мин", "extension:10"),
            new TelegramApi.Button("+15 мин", "extension:15"),
            new TelegramApi.Button("+20 мин", "extension:20"),
            new TelegramApi.Button("+30 мин", "extension:30"));

    private final SecurityService securityService;
    private final TelegramApi telegramApi;
    private final DynamoIngestionService ingestionService;
    private final SessionRepository sessionRepository;
    private final TimerService timerService;

    /**
     * Default constructor used by AWS Lambda.
     * Initializes services from environment variables.
     */
    public BotHandler() {
        this.securityService = SecurityService.fromEnvironment();
        this.telegramApi = TelegramService.fromEnvironment();
        this.sessionRepository = DynamoSessionRepository.create();
        this.timerService = SchedulerTimerService.fromEnvironment();
        this.ingestionService = DynamoIngestionService.create();
    }

    /**
     * Constructor for testing with injected dependencies.
     */
    public BotHandler(
            final SecurityService securityService,
            final TelegramApi telegramApi,
            final DynamoIngestionService ingestionService,
            final SessionRepository sessionRepository,
            final TimerService timerService) {
        this.securityService = securityService;
        this.telegramApi = telegramApi;
        this.ingestionService = ingestionService;
        this.sessionRepository = sessionRepository;
        this.timerService = timerService;
    }

    @Override
    public Map<String, Object> handleRequest(final Map<String, Object> input, final Context context) {
        try {
            // Check if this is an EventBridge timer event
            if (input.containsKey("action") && "TIMER_DONE".equals(input.get("action"))) {
                handleTimerDone(input, context);
                return successResponse();
            }

            // Otherwise, treat as API Gateway event (Telegram webhook)
            final String body = (String) input.get("body");
            if (body == null || body.isBlank()) {
                return successResponse();
            }

            final TelegramApi.Update update = telegramApi.parseUpdate(body);
            if (update == null) {
                return successResponse();
            }

            // Security check: only allow the admin user
            if (!securityService.isAuthorized(update.chatId())) {
                context.getLogger().log("Unauthorized access attempt from chatId: " + update.chatId());
                return successResponse();
            }

            // Get current session state
            final SessionData session = sessionRepository.getSession(update.chatId());
            context.getLogger().log("Processing update for chatId: " + update.chatId() + ", session status: "
                    + (session != null ? session.status() : "null"));

            // Route based on state and update type
            routeUpdate(update, session, context);

            return successResponse();

        } catch (Exception e) {
            context.getLogger()
                    .log("CRITICAL ERROR processing request: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            return successResponse();
        }
    }

    private void handleTimerDone(final Map<String, Object> input, final Context context)
            throws IOException, InterruptedException {
        final long chatId = ((Number) input.get("chatId")).longValue();

        // Verify chat is authorized
        if (!securityService.isAuthorized(chatId)) {
            return;
        }

        final SessionData session = sessionRepository.getSession(chatId);

        // Only process if session is in WORKING state
        if (session.status() != SessionState.WORKING) {
            context.getLogger()
                    .log("Timer done event ignored. Session for chatId " + chatId + " is in state " + session.status());
            return;
        }

        context.getLogger().log("Timer done for chatId: " + chatId + ". Transitioning to WAITING_FOR_EXTENSION.");

        // Transition to WAITING_FOR_EXTENSION to offer extension or finish
        sessionRepository.saveSession(session.waitingForExtension());
        telegramApi.sendMessageWithKeyboard(chatId, "Время вышло. Что делаем дальше?", EXTENSION_BUTTONS);
    }

    private void routeUpdate(final TelegramApi.Update update, final SessionData session, final Context context)
            throws IOException, InterruptedException {

        // Handle /start command - always allowed (resets flow)
        if (update.isStartCommand()) {
            handleStartCommand(update.chatId(), session);
            return;
        }

        // Handle /stop command - only during WORKING state
        if (update.isStopCommand()) {
            handleStopCommand(update.chatId(), session, context);
            return;
        }

        // Handle callback queries
        if (update.isCallbackQuery()) {
            context.getLogger()
                    .log("Handling callback query: " + update.callbackData() + " for chatId: " + update.chatId());
            telegramApi.answerCallbackQuery(update.callbackQueryId());
            handleCallbackQuery(update.chatId(), update.callbackData(), session, context);
            return;
        }

        // Handle text messages based on state
        if (update.isTextMessage() && !update.isCommand()) {
            context.getLogger().log("Handling text message: '" + update.text() + "' for chatId: " + update.chatId()
                    + " in state: " + session.status());
            handleTextMessage(update.chatId(), update.text(), session, context);
        }
    }

    private void handleStartCommand(final long chatId, final SessionData session)
            throws IOException, InterruptedException {
        // Cancel any existing timer if there's an active session
        if (session.status() == SessionState.WORKING && session.scheduleName() != null) {
            timerService.cancelTimer(session.scheduleName());
        }

        // Transition to WAITING_FOR_DURATION
        sessionRepository.saveSession(SessionData.idle(chatId).waitingForDuration());

        telegramApi.sendMessageWithKeyboard(
                chatId,
                "Выберите продолжительность сессии (минуты):",
                DURATION_BUTTONS);
    }

    private void handleStopCommand(final long chatId, final SessionData session, final Context context)
            throws IOException, InterruptedException {
        if (session.status() != SessionState.WORKING) {
            telegramApi.sendMessage(chatId, "Нет активной сессии для остановки.");
            return;
        }

        // Cancel the EventBridge schedule
        timerService.cancelTimer(session.scheduleName());

        sessionRepository.saveSession(session.waitingForOutcome());
        telegramApi.sendMessage(chatId, "Сессия остановлена. Каков результат? (Что сделано + рефлексия)");
    }

    private void handleCallbackQuery(final long chatId, final String callbackData,
            final SessionData session, final Context context)
            throws IOException, InterruptedException {

        if (callbackData.startsWith("duration:") && session.status() == SessionState.WAITING_FOR_DURATION) {
            final int duration = Integer.parseInt(callbackData.substring("duration:".length()));
            sessionRepository.saveSession(session.waitingForTask(duration));
            telegramApi.sendMessage(chatId, "Что ты собираешься делать? (Один глагол, описание метода)");
            return;
        }

        if (callbackData.startsWith("role:") && session.status() == SessionState.WAITING_FOR_ROLE) {
            final String role = callbackData.substring("role:".length());
            handleRoleInput(chatId, role, session);
            return;
        }

        if (callbackData.startsWith("extension:") && session.status() == SessionState.WAITING_FOR_EXTENSION) {
            final String action = callbackData.substring("extension:".length());
            context.getLogger().log("Extension choice: " + action + " for chatId: " + chatId);
            if ("finish".equals(action)) {
                // Transition to reflection phase (Outcome)
                sessionRepository.saveSession(session.waitingForOutcome());
                telegramApi.sendMessage(chatId, "Каков результат? (Что сделано + рефлексия)");
            } else {
                // Extend the session
                final int extensionMinutes = Integer.parseInt(action);
                final String newScheduleName = timerService.createTimer(chatId, extensionMinutes);
                sessionRepository.saveSession(session.workingExtended(newScheduleName));
                telegramApi.sendMessage(chatId,
                        String.format("Таймер продлен на %d минут. Работаем.", extensionMinutes));
            }
            return;
        }
        context.getLogger().log("Unhandled callback query: " + callbackData + " in state: " + session.status());
    }

    private void handleTextMessage(final long chatId, final String text,
            final SessionData session, final Context context)
            throws IOException, InterruptedException {

        switch (session.status()) {
            case WAITING_FOR_TASK -> {
                sessionRepository.saveSession(session.waitingForRole(text));
                telegramApi.sendMessageWithKeyboard(chatId, "В какой роли ты это будешь делать?", ROLE_BUTTONS);
            }
            case WAITING_FOR_ROLE -> handleRoleInput(chatId, text, session); // Handle manual text input for Role
            case WAITING_FOR_PRODUCT_TYPE -> startWorkingSession(chatId, text, session); // Text is product type, start
                                                                                         // immediately

            // Reflection Text Inputs
            case WAITING_FOR_OUTCOME -> finishSession(chatId, text, session, context);

            case IDLE -> {
                telegramApi.sendMessage(chatId, "Я в режиме ожидания. Напиши /start чтобы начать сессию.");
            }
            default -> {
                context.getLogger().log("Unhandled text message in state: " + session.status());
            }
        }
    }

    private void handleRoleInput(final long chatId, final String role, final SessionData session)
            throws IOException, InterruptedException {
        sessionRepository.saveSession(session.waitingForProductType(role));
        telegramApi.sendMessage(chatId, "Какой рабочий продукт рассчитываешь получить? (Заготовка, код и т.п.)");
    }

    private void startWorkingSession(final long chatId, final String productType, final SessionData session)
            throws IOException, InterruptedException {
        final Instant startTime = Instant.now();
        final String scheduleName = timerService.createTimer(chatId, session.duration());

        sessionRepository.saveSession(session.working(productType, startTime, scheduleName));
        telegramApi.sendMessage(chatId, String.format("Таймер запущен на %d минут. Работаем.", session.duration()));
    }

    private void finishSession(final long chatId, final String outcome, final SessionData session,
            final Context context)
            throws IOException, InterruptedException {

        // Construct the payload for ingestion
        final Instant endTime = Instant.now();
        IngestionPayload payload = new IngestionPayload(
                session.task(),
                session.role(),
                session.productType(),
                session.startTime(),
                endTime,
                session.duration(),
                outcome);

        context.getLogger()
                .log("Attempting to ingest session. Task: " + payload.task());

        try {
            ingestionService.ingestSession(payload);
            context.getLogger().log("Session ingested successfully for chatId: " + chatId);

            sessionRepository.deleteSession(chatId);
            telegramApi.sendMessage(chatId, "Сессия сохранена. Отдыхаем.");
        } catch (Exception e) {
            context.getLogger()
                    .log("FAILED to ingest session: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            telegramApi.sendMessage(chatId, "Ошибка при сохранении сессии. Проверьте логи.");
        }
    }

    private Map<String, Object> successResponse() {
        return Map.of(
                "statusCode", 200,
                "body", "{\"ok\":true}");
    }
}
