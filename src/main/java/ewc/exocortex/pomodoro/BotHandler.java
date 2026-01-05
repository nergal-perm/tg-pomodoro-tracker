package ewc.exocortex.pomodoro;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * AWS Lambda handler for the Pomodoro Bot.
 * Routes incoming events from Telegram Webhook or EventBridge Scheduler.
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

    private static final List<TelegramApi.Button> ENERGY_BUTTONS = List.of(
            new TelegramApi.Button("5-Пиковый", "energy:5"),
            new TelegramApi.Button("4-Потоковый", "energy:4"),
            new TelegramApi.Button("3-Функциональный", "energy:3"),
            new TelegramApi.Button("2-Упадок", "energy:2"),
            new TelegramApi.Button("1-Истощение", "energy:1"),
            new TelegramApi.Button("0-Критический", "energy:0"));

    private static final List<TelegramApi.Button> FOCUS_BUTTONS = List.of(
            new TelegramApi.Button("3-Предельный", "focus:3"),
            new TelegramApi.Button("2-Обычный", "focus:2"),
            new TelegramApi.Button("1-Рассеянный", "focus:1"));

    private static final List<TelegramApi.Button> QUALITY_BUTTONS = List.of(
            new TelegramApi.Button("3-Исключительное", "quality:3"),
            new TelegramApi.Button("2-Приемлемое", "quality:2"),
            new TelegramApi.Button("1-Низкое", "quality:1"));

    private static final List<TelegramApi.Button> EXTENSION_BUTTONS = List.of(
            new TelegramApi.Button("Завершить", "extension:finish"),
            new TelegramApi.Button("+5 мин", "extension:5"),
            new TelegramApi.Button("+10 мин", "extension:10"),
            new TelegramApi.Button("+15 мин", "extension:15"),
            new TelegramApi.Button("+20 мин", "extension:20"),
            new TelegramApi.Button("+30 мин", "extension:30"));

    private final SecurityService securityService;
    private final TelegramApi telegramApi;
    private final DriveApi driveApi;
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
        try {
            this.driveApi = DriveService.fromEnvironment();
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Failed to initialize DriveService", e);
        }
    }

    /**
     * Constructor for testing with injected dependencies.
     */
    public BotHandler(
            final SecurityService securityService,
            final TelegramApi telegramApi,
            final DriveApi driveApi,
            final SessionRepository sessionRepository,
            final TimerService timerService) {
        this.securityService = securityService;
        this.telegramApi = telegramApi;
        this.driveApi = driveApi;
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

        sessionRepository.saveSession(session.waitingForEnergy());
        telegramApi.sendMessageWithKeyboard(chatId, "Сессия остановлена. Каков был уровень энергии?", ENERGY_BUTTONS);
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

        if (callbackData.startsWith("energy:") && session.status() == SessionState.WAITING_FOR_ENERGY) {
            final String energy = callbackData.substring("energy:".length());
            sessionRepository.saveSession(session.waitingForFocus(energy));
            telegramApi.sendMessageWithKeyboard(chatId, "Каков был уровень фокуса?", FOCUS_BUTTONS);
            return;
        }

        if (callbackData.startsWith("focus:") && session.status() == SessionState.WAITING_FOR_FOCUS) {
            final String focus = callbackData.substring("focus:".length());
            sessionRepository.saveSession(session.waitingForQuality(focus));
            telegramApi.sendMessageWithKeyboard(chatId, "Каково качество рабочего продукта?", QUALITY_BUTTONS);
            return;
        }

        if (callbackData.startsWith("quality:") && session.status() == SessionState.WAITING_FOR_QUALITY) {
            final String quality = callbackData.substring("quality:".length());
            sessionRepository.saveSession(session.waitingForSummary(quality));
            telegramApi.sendMessage(chatId, "Подведите краткий итог сессии.");
            return;
        }

        if (callbackData.startsWith("extension:") && session.status() == SessionState.WAITING_FOR_EXTENSION) {
            final String action = callbackData.substring("extension:".length());
            context.getLogger().log("Extension choice: " + action + " for chatId: " + chatId);
            if ("finish".equals(action)) {
                // Transition to reflection phase
                sessionRepository.saveSession(session.waitingForEnergy());
                telegramApi.sendMessageWithKeyboard(chatId, "Каков был уровень энергии?", ENERGY_BUTTONS);
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
            case WAITING_FOR_PRODUCT_TYPE -> {
                sessionRepository.saveSession(session.waitingForUsageContext(text));
                telegramApi.sendMessage(chatId, "Где и при каких условиях этот рабочий продукт будет применен?");
            }
            case WAITING_FOR_USAGE_CONTEXT -> {
                sessionRepository.saveSession(session.waitingForContext(text));
                telegramApi.sendMessage(chatId, "Каков контекст рабочей сессии? (Ситуация, причина, триггер)");
            }
            case WAITING_FOR_CONTEXT -> {
                sessionRepository.saveSession(session.waitingForResources(text));
                telegramApi.sendMessage(chatId, "Каковы ресурсы на входе?");
            }
            case WAITING_FOR_RESOURCES -> {
                sessionRepository.saveSession(session.waitingForConstraints(text));
                telegramApi.sendMessage(chatId, "Ограничения, если есть?");
            }
            case WAITING_FOR_CONSTRAINTS -> startWorkingSession(chatId, text, session);

            // Reflection Text Inputs
            case WAITING_FOR_SUMMARY -> {
                sessionRepository.saveSession(session.waitingForNextStep(text));
                telegramApi.sendMessage(chatId, "Каков следующий шаг?");
            }
            case WAITING_FOR_NEXT_STEP -> finishSession(chatId, text, session, context);

            case IDLE -> {
                // In IDLE, just save the message to Drive as a simple note (legacy behavior /
                // bot-core spec)
                context.getLogger().log("Saving quick note from IDLE state for chatId: " + chatId);
                final String fileName = formatFileName(Instant.now(), "Quick Note");
                try {
                    driveApi.uploadNote(fileName, text);
                    telegramApi.sendMessage(chatId, "Информация о рабочей сессии сохранена на Диск!");
                } catch (Exception e) {
                    context.getLogger().log("FAILED to save quick note to Drive: " + e.getMessage());
                    e.printStackTrace();
                    telegramApi.sendMessage(chatId, "Ошибка при сохранении на Диск. Попробуйте позже.");
                }
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

    private void startWorkingSession(final long chatId, final String constraints, final SessionData session)
            throws IOException, InterruptedException {
        final Instant startTime = Instant.now();
        final String scheduleName = timerService.createTimer(chatId, session.duration());

        sessionRepository.saveSession(session.working(constraints, startTime, scheduleName));
        telegramApi.sendMessage(chatId, String.format("Таймер запущен на %d минут. Работаем.", session.duration()));
    }

    private void finishSession(final long chatId, final String nextStep, final SessionData session,
            final Context context)
            throws IOException, InterruptedException {

        SessionData completedSession = new SessionData(
                session.chatId(),
                SessionState.IDLE, // Final state
                session.duration(),
                session.scheduleName(),
                session.task(),
                session.role(),
                session.productType(),
                session.usageContext(),
                session.workContext(),
                session.resources(),
                session.constraints(),
                session.startTime(),
                session.energyLevel(),
                session.focusLevel(),
                session.qualityLevel(),
                session.summary(),
                nextStep // The final piece
        );

        final Instant stopTime = Instant.now();
        final String noteContent = NoteFormatter.format(completedSession, stopTime);
        final String fileName = formatFileName(completedSession.startTime(), completedSession.task());

        context.getLogger()
                .log("Attempting to save session note. FileName: " + fileName + ", Task: " + completedSession.task());

        try {
            driveApi.uploadNote(fileName, noteContent);
            context.getLogger().log("Session note saved successfully for chatId: " + chatId);

            sessionRepository.deleteSession(chatId);
            telegramApi.sendMessage(chatId, "Сессия сохранена. Отдыхаем.");
        } catch (Exception e) {
            context.getLogger()
                    .log("FAILED to save session note to Drive: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            telegramApi.sendMessage(chatId, "Ошибка при сохранении сессии на Google Drive. Проверьте логи.");
            // We DON'T delete the session if it fails to save, so user might be able to
            // retry?
            // Actually, they might need to re-enter final step.
        }
    }

    private String formatFileName(final Instant startTime, final String title) {
        final String actualTitle = (title == null || title.isBlank()) ? "Untitled Session" : title;
        final String safeTitle = actualTitle.replaceAll("[^a-zA-Z0-9а-яА-Я ]", "").trim();
        final String shortTitle = safeTitle.length() > 50 ? safeTitle.substring(0, 50) : safeTitle;

        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd' 'HH-mm")
                .withZone(java.time.ZoneId.of("Asia/Tbilisi"));
        final String dateTime = formatter.format(startTime);
        return dateTime + " - " + shortTitle + ".md";
    }

    private Map<String, Object> successResponse() {
        return Map.of(
                "statusCode", 200,
                "body", "{\"ok\":true}");
    }
}
