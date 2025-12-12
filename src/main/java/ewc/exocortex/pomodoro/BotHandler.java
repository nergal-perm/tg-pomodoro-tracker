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
            new TelegramApi.Button("5 min", "duration:5"),
            new TelegramApi.Button("30 min", "duration:30"),
            new TelegramApi.Button("45 min", "duration:45"),
            new TelegramApi.Button("60 min", "duration:60"),
            new TelegramApi.Button("90 min", "duration:90"));

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

            // Route based on state and update type
            routeUpdate(update, session, context);

            return successResponse();

        } catch (Exception e) {
            context.getLogger().log("Error processing request: " + e.getMessage());
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
            return;
        }

        // Transition to WAITING_FOR_RESULT
        sessionRepository.saveSession(session.waitingForResult());
        telegramApi.sendMessage(chatId, "⏰ Time's up! Tell me what you have done.");
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

        // Handle callback queries (duration selection)
        if (update.isCallbackQuery()) {
            telegramApi.answerCallbackQuery(update.callbackQueryId());
            handleCallbackQuery(update.chatId(), update.callbackData(), session, context);
            return;
        }

        // Handle text messages based on state
        if (update.isTextMessage() && !update.isCommand()) {
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
                "How long do you want to work?",
                DURATION_BUTTONS);
    }

    private void handleStopCommand(final long chatId, final SessionData session, final Context context)
            throws IOException, InterruptedException {
        if (session.status() != SessionState.WORKING) {
            telegramApi.sendMessage(chatId, "No active session to stop.");
            return;
        }

        // Cancel the EventBridge schedule
        timerService.cancelTimer(session.scheduleName());

        sessionRepository.saveSession(session.waitingForResult());
        telegramApi.sendMessage(chatId, "Session stopped. Tell me what you have done.");
    }

    private void handleCallbackQuery(final long chatId, final String callbackData,
            final SessionData session, final Context context)
            throws IOException, InterruptedException {

        if (session.status() != SessionState.WAITING_FOR_DURATION) {
            return;
        }

        if (callbackData.startsWith("duration:")) {
            final int duration = Integer.parseInt(callbackData.substring("duration:".length()));

            sessionRepository.saveSession(session.waitingForTitle(duration));
            telegramApi.sendMessage(chatId,
                    String.format("Duration set to %d minutes. What are you working on?", duration));
        }
    }

    private void handleTextMessage(final long chatId, final String text,
            final SessionData session, final Context context)
            throws IOException, InterruptedException {

        switch (session.status()) {
            case WAITING_FOR_TITLE -> handleTitleInput(chatId, text, session);
            case WAITING_FOR_RESULT -> handleResultInput(chatId, text, session);
            default -> {
                // In IDLE or other states, ignore plain text
            }
        }
    }

    private void handleTitleInput(final long chatId, final String title, final SessionData session)
            throws IOException, InterruptedException {

        final Instant startTime = Instant.now();

        // Create EventBridge schedule
        final String scheduleName = timerService.createTimer(chatId, session.duration());

        // Transition to WORKING
        sessionRepository.saveSession(session.working(title, startTime, scheduleName));

        telegramApi.sendMessage(chatId,
                String.format("⏱️ Timer started for '%s' (%d minutes). Focus!", title, session.duration()));
    }

    private void handleResultInput(final long chatId, final String result, final SessionData session)
            throws IOException, InterruptedException {

        final Instant stopTime = Instant.now();

        // Format and upload note
        final String noteContent = formatNote(session, stopTime, result);
        final String fileName = formatFileName(session.startTime(), session.sessionTitle());

        driveApi.uploadNote(fileName, noteContent);

        // Clear session state
        sessionRepository.deleteSession(chatId);

        telegramApi.sendMessage(chatId, "✅ Session saved to Drive!");
    }

    private String formatNote(final SessionData session, final Instant stopTime, final String result) {
        return String.format("""
                ---
                started: %s
                stopped: %s
                timer_setting: %d
                ---

                %s
                """,
                session.startTime().toString(),
                stopTime.toString(),
                session.duration(),
                result);
    }

    private String formatFileName(final Instant startTime, final String title) {
        final String date = LocalDate.ofInstant(startTime, ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
        return date + " " + title + ".md";
    }

    private Map<String, Object> successResponse() {
        return Map.of(
                "statusCode", 200,
                "body", "{\"ok\":true}");
    }
}
