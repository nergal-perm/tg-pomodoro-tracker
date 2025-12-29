package ewc.exocortex.pomodoro

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import java.io.IOException
import java.security.GeneralSecurityException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Map

/**
 * AWS Lambda handler for the Pomodoro Bot.
 * Routes incoming events from Telegram Webhook or EventBridge Scheduler.
 */
class BotHandler : RequestHandler<MutableMap<String?, Any?>, MutableMap<String?, Any?>> {
    private val securityService: SecurityService
    private val telegramApi: TelegramApi
    private val driveApi: DriveApi
    private val sessionRepository: SessionRepository
    private val timerService: TimerService

    /**
     * Default constructor used by AWS Lambda.
     * Initializes services from environment variables.
     */
    constructor() {
        this.securityService = SecurityService.fromEnvironment()
        this.telegramApi = TelegramService.fromEnvironment()
        this.sessionRepository = DynamoSessionRepository.create()
        this.timerService = SchedulerTimerService.fromEnvironment()
        try {
            this.driveApi = DriveService.fromEnvironment()
        } catch (e: IOException) {
            throw IllegalStateException("Failed to initialize DriveService", e)
        } catch (e: GeneralSecurityException) {
            throw IllegalStateException("Failed to initialize DriveService", e)
        }
    }

    /**
     * Constructor for testing with injected dependencies.
     */
    constructor(
        securityService: SecurityService,
        telegramApi: TelegramApi,
        driveApi: DriveApi,
        sessionRepository: SessionRepository,
        timerService: TimerService
    ) {
        this.securityService = securityService
        this.telegramApi = telegramApi
        this.driveApi = driveApi
        this.sessionRepository = sessionRepository
        this.timerService = timerService
    }

    override fun handleRequest(input: MutableMap<String?, Any?>, context: Context): MutableMap<String?, Any?> {
        try {
            // Check if this is an EventBridge timer event
            if (input.containsKey("action") && "TIMER_DONE" == input["action"]) {
                handleTimerDone(input, context)
                return successResponse()
            }

            // Otherwise, treat as API Gateway event (Telegram webhook)
            val body = input["body"] as String?
            if (body == null || body.isBlank()) {
                return successResponse()
            }

            val update = telegramApi.parseUpdate(body) ?: return successResponse()

            // Security check: only allow the admin user
            if (!securityService.isAuthorized(update.chatId)) {
                context.logger.log("Unauthorized access attempt from chatId: ${update.chatId}")
                return successResponse()
            }

            // Get current session state
            val session = sessionRepository.getSession(update.chatId)
            context.logger.log(
                ("Processing update for chatId: ${update.chatId}, session status: ${if (session != null) session.status else "null"}")
            )

            // Route based on state and update type
            routeUpdate(update, session!!, context)

            return successResponse()
        } catch (e: Exception) {
            context.logger.log("CRITICAL ERROR processing request: ${e.javaClass.getName()}: ${e.message}")
            e.printStackTrace()
            return successResponse()
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun handleTimerDone(input: MutableMap<String?, Any?>, context: Context) {
        val chatId = (input["chatId"] as Number).toLong()

        // Verify chat is authorized
        if (!securityService.isAuthorized(chatId)) {
            return
        }

        val session = sessionRepository.getSession(chatId)

        // Only process if session is in WORKING state
        if (session.status != SessionState.WORKING) {
            context.logger.log("Timer done event ignored. Session for chatId $chatId is in state ${session.status}")
            return
        }

        context.logger.log("Timer done for chatId: $chatId. Transitioning to WAITING_FOR_EXTENSION.")

        // Transition to WAITING_FOR_EXTENSION to offer extension or finish
        sessionRepository.saveSession(session.waitingForExtension())
        telegramApi.sendMessageWithKeyboard(chatId, "Время вышло. Что делаем дальше?", EXTENSION_BUTTONS)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun routeUpdate(update: TelegramApi.Update, session: SessionData, context: Context) {
        // Handle /start command - always allowed (resets flow)

        if (update.isStartCommand) {
            handleStartCommand(update.chatId, session)
            return
        }

        // Handle /stop command - only during WORKING state
        if (update.isStopCommand) {
            handleStopCommand(update.chatId, session)
            return
        }

        // Handle callback queries
        if (update.isCallbackQuery) {
            context.logger.log("Handling callback query: ${update.callbackData} for chatId: ${update.chatId}")
            telegramApi.answerCallbackQuery(update.callbackQueryId)
            handleCallbackQuery(update.chatId, update.callbackData, session, context)
            return
        }

        // Handle text messages based on state
        if (update.isTextMessage && !update.isCommand) {
            context.logger.log(("Handling text message: '${update.text}' for chatId: ${update.chatId} in state: ${session.status}"))
            handleTextMessage(update.chatId, update.text, session, context)
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun handleStartCommand(chatId: Long, session: SessionData) {
        // Cancel any existing timer if there's an active session
        if (session.status == SessionState.WORKING && session.scheduleName != null) {
            timerService.cancelTimer(session.scheduleName)
        }

        // Transition to WAITING_FOR_DURATION
        sessionRepository.saveSession(SessionData.idle(chatId).waitingForDuration())

        telegramApi.sendMessageWithKeyboard(
            chatId,
            "Выберите продолжительность сессии (минуты):",
            DURATION_BUTTONS
        )
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun handleStopCommand(chatId: Long, session: SessionData) {
        if (session.status != SessionState.WORKING) {
            telegramApi.sendMessage(chatId, "Нет активной сессии для остановки.")
            return
        }

        // Cancel the EventBridge schedule
        timerService.cancelTimer(session.scheduleName)

        sessionRepository.saveSession(session.waitingForEnergy())
        telegramApi.sendMessageWithKeyboard(chatId, "Сессия остановлена. Каков был уровень энергии?", ENERGY_BUTTONS)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun handleCallbackQuery(
        chatId: Long, callbackData: String,
        session: SessionData, context: Context
    ) {
        if (callbackData.startsWith("duration:") && session.status == SessionState.WAITING_FOR_DURATION) {
            val duration = callbackData.substring("duration:".length).toInt()
            sessionRepository.saveSession(session.waitingForTask(duration))
            telegramApi.sendMessage(chatId, "Что ты собираешься делать? (Один глагол, описание метода)")
            return
        }

        if (callbackData.startsWith("role:") && session.status == SessionState.WAITING_FOR_ROLE) {
            val role = callbackData.substring("role:".length)
            handleRoleInput(chatId, role, session)
            return
        }

        if (callbackData.startsWith("energy:") && session.status == SessionState.WAITING_FOR_ENERGY) {
            val energy = callbackData.substring("energy:".length)
            sessionRepository.saveSession(session.waitingForFocus(energy))
            telegramApi.sendMessageWithKeyboard(chatId, "Каков был уровень фокуса?", FOCUS_BUTTONS)
            return
        }

        if (callbackData.startsWith("focus:") && session.status == SessionState.WAITING_FOR_FOCUS) {
            val focus = callbackData.substring("focus:".length)
            sessionRepository.saveSession(session.waitingForQuality(focus))
            telegramApi.sendMessageWithKeyboard(chatId, "Каково качество рабочего продукта?", QUALITY_BUTTONS)
            return
        }

        if (callbackData.startsWith("quality:") && session.status == SessionState.WAITING_FOR_QUALITY) {
            val quality = callbackData.substring("quality:".length)
            sessionRepository.saveSession(session.waitingForSummary(quality))
            telegramApi.sendMessage(chatId, "Подведите краткий итог сессии.")
            return
        }

        if (callbackData.startsWith("extension:") && session.status == SessionState.WAITING_FOR_EXTENSION) {
            val action = callbackData.substring("extension:".length)
            context.logger.log("Extension choice: $action for chatId: $chatId")
            if ("finish" == action) {
                // Transition to reflection phase
                sessionRepository.saveSession(session.waitingForEnergy())
                telegramApi.sendMessageWithKeyboard(chatId, "Каков был уровень энергии?", ENERGY_BUTTONS)
            } else {
                // Extend the session
                val extensionMinutes = action.toInt()
                val newScheduleName = timerService.createTimer(chatId, extensionMinutes)
                sessionRepository.saveSession(session.workingExtended(newScheduleName))
                telegramApi.sendMessage(
                    chatId,
                    String.format("Таймер продлен на %d минут. Работаем.", extensionMinutes)
                )
            }
            return
        }
        context.logger.log("Unhandled callback query: " + callbackData + " in state: " + session.status)
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun handleTextMessage(
        chatId: Long, text: String?,
        session: SessionData, context: Context
    ) {
        when (session.status) {
            SessionState.WAITING_FOR_TASK -> {
                sessionRepository.saveSession(session.waitingForRole(text))
                telegramApi.sendMessageWithKeyboard(chatId, "В какой роли ты это будешь делать?", ROLE_BUTTONS)
            }

            SessionState.WAITING_FOR_ROLE -> handleRoleInput(chatId, text, session)
            SessionState.WAITING_FOR_PRODUCT_TYPE -> {
                sessionRepository.saveSession(session.waitingForUsageContext(text))
                telegramApi.sendMessage(chatId, "Где и при каких условиях этот рабочий продукт будет применен?")
            }

            SessionState.WAITING_FOR_USAGE_CONTEXT -> {
                sessionRepository.saveSession(session.waitingForContext(text))
                telegramApi.sendMessage(chatId, "Каков контекст рабочей сессии? (Ситуация, причина, триггер)")
            }

            SessionState.WAITING_FOR_CONTEXT -> {
                sessionRepository.saveSession(session.waitingForResources(text))
                telegramApi.sendMessage(chatId, "Каковы ресурсы на входе?")
            }

            SessionState.WAITING_FOR_RESOURCES -> {
                sessionRepository.saveSession(session.waitingForConstraints(text))
                telegramApi.sendMessage(chatId, "Ограничения, если есть?")
            }

            SessionState.WAITING_FOR_CONSTRAINTS -> startWorkingSession(chatId, text, session)
            SessionState.WAITING_FOR_SUMMARY -> {
                sessionRepository.saveSession(session.waitingForNextStep(text))
                telegramApi.sendMessage(chatId, "Каков следующий шаг?")
            }

            SessionState.WAITING_FOR_NEXT_STEP -> finishSession(chatId, text, session, context)
            SessionState.IDLE -> {
                // In IDLE, just save the message to Drive as a simple note (legacy behavior /
                // bot-core spec)
                context.logger.log("Saving quick note from IDLE state for chatId: $chatId")
                val fileName = formatFileName(Instant.now(), "Quick Note")
                try {
                    driveApi.uploadNote(fileName, text)
                    telegramApi.sendMessage(chatId, "Информация о рабочей сессии сохранена на Диск!")
                } catch (e: Exception) {
                    context.logger.log("FAILED to save quick note to Drive: " + e.message)
                    e.printStackTrace()
                    telegramApi.sendMessage(chatId, "Ошибка при сохранении на Диск. Попробуйте позже.")
                }
            }

            else -> {
                context.logger.log("Unhandled text message in state: " + session.status)
            }
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun handleRoleInput(chatId: Long, role: String?, session: SessionData) {
        sessionRepository.saveSession(session.waitingForProductType(role))
        telegramApi.sendMessage(chatId, "Какой рабочий продукт рассчитываешь получить? (Заготовка, код и т.п.)")
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun startWorkingSession(chatId: Long, constraints: String?, session: SessionData) {
        val startTime = Instant.now()
        val scheduleName = timerService.createTimer(chatId, session.duration)

        sessionRepository.saveSession(session.working(constraints, startTime, scheduleName))
        telegramApi.sendMessage(chatId, String.format("Таймер запущен на %d минут. Работаем.", session.duration))
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun finishSession(
        chatId: Long, nextStep: String?, session: SessionData,
        context: Context
    ) {
        val completedSession = SessionData(
            session.chatId,
            SessionState.IDLE,  // Final state
            session.duration,
            session.scheduleName,
            session.task,
            session.role,
            session.productType,
            session.usageContext,
            session.workContext,
            session.resources,
            session.constraints,
            session.startTime,
            session.energyLevel,
            session.focusLevel,
            session.qualityLevel,
            session.summary,
            nextStep // The final piece
        )

        val stopTime = Instant.now()
        val noteContent = NoteFormatter.format(completedSession, stopTime)
        val fileName = formatFileName(completedSession.startTime, completedSession.task)

        context.logger
            .log("Attempting to save session note. FileName: " + fileName + ", Task: " + completedSession.task)

        try {
            driveApi.uploadNote(fileName, noteContent)
            context.logger.log("Session note saved successfully for chatId: $chatId")

            sessionRepository.deleteSession(chatId)
            telegramApi.sendMessage(chatId, "Сессия сохранена. Отдыхаем.")
        } catch (e: Exception) {
            context.logger
                .log("FAILED to save session note to Drive: " + e.javaClass.getName() + ": " + e.message)
            e.printStackTrace()
            telegramApi.sendMessage(chatId, "Ошибка при сохранении сессии на Google Drive. Проверьте логи.")
            // We DON'T delete the session if it fails to save, so user might be able to
            // retry?
            // Actually, they might need to re-enter final step.
        }
    }

    private fun formatFileName(startTime: Instant, title: String?): String {
        val actualTitle = if (title == null || title.isBlank()) "Untitled Session" else title
        val safeTitle = actualTitle.replace("[^a-zA-Z0-9а-яА-Я ]".toRegex(), "").trim { it <= ' ' }
        val shortTitle = if (safeTitle.length > 50) safeTitle.take(50) else safeTitle

        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd' 'HH-mm")
            .withZone(ZoneId.of("Asia/Tbilisi"))
        val dateTime = formatter.format(startTime)
        return "$dateTime - $shortTitle.md"
    }

    private fun successResponse(): MutableMap<String?, Any?> {
        return Map.of<String?, Any?>(
            "statusCode", 200,
            "body", "{\"ok\":true}"
        )
    }

    companion object {
        private val DURATION_BUTTONS: List<TelegramApi.Button?> = listOf<TelegramApi.Button?>(
            TelegramApi.Button("5 минут", "duration:5"),
            TelegramApi.Button("30 минут", "duration:30"),
            TelegramApi.Button("45 минут", "duration:45"),
            TelegramApi.Button("60 минут", "duration:60"),
            TelegramApi.Button("90 минут", "duration:90")
        )

        private val ROLE_BUTTONS: List<TelegramApi.Button?> = listOf<TelegramApi.Button?>(
            TelegramApi.Button("Ученик", "role:ученик"),
            TelegramApi.Button("Интеллектуал", "role:интеллектуал"),
            TelegramApi.Button("Профессионал", "role:профессионал"),
            TelegramApi.Button("Исследователь", "role:исследователь"),
            TelegramApi.Button("Просветитель", "role:просветитель")
        )

        private val ENERGY_BUTTONS: List<TelegramApi.Button?> = listOf<TelegramApi.Button?>(
            TelegramApi.Button("5-Пиковый", "energy:5"),
            TelegramApi.Button("4-Потоковый", "energy:4"),
            TelegramApi.Button("3-Функциональный", "energy:3"),
            TelegramApi.Button("2-Упадок", "energy:2"),
            TelegramApi.Button("1-Истощение", "energy:1"),
            TelegramApi.Button("0-Критический", "energy:0")
        )

        private val FOCUS_BUTTONS: List<TelegramApi.Button?> = listOf<TelegramApi.Button?>(
            TelegramApi.Button("3-Предельный", "focus:3"),
            TelegramApi.Button("2-Обычный", "focus:2"),
            TelegramApi.Button("1-Рассеянный", "focus:1")
        )

        private val QUALITY_BUTTONS: List<TelegramApi.Button?> = listOf<TelegramApi.Button?>(
            TelegramApi.Button("3-Исключительное", "quality:3"),
            TelegramApi.Button("2-Приемлемое", "quality:2"),
            TelegramApi.Button("1-Низкое", "quality:1")
        )

        private val EXTENSION_BUTTONS: List<TelegramApi.Button?> = listOf<TelegramApi.Button?>(
            TelegramApi.Button("Завершить", "extension:finish"),
            TelegramApi.Button("+5 мин", "extension:5"),
            TelegramApi.Button("+10 мин", "extension:10"),
            TelegramApi.Button("+15 мин", "extension:15"),
            TelegramApi.Button("+20 мин", "extension:20"),
            TelegramApi.Button("+30 мин", "extension:30")
        )
    }
}