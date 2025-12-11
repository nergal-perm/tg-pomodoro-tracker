package ewc.exocortex.pomodoro;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * AWS Lambda handler for the Pomodoro Bot.
 * Routes incoming events from Telegram Webhook.
 */
public class BotHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final SecurityService securityService;
    private final TelegramApi telegramApi;
    private final DriveApi driveApi;

    /**
     * Default constructor used by AWS Lambda.
     * Initializes services from environment variables.
     */
    public BotHandler() {
        this.securityService = SecurityService.fromEnvironment();
        this.telegramApi = TelegramService.fromEnvironment();
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
            final DriveApi driveApi) {
        this.securityService = securityService;
        this.telegramApi = telegramApi;
        this.driveApi = driveApi;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(
            final APIGatewayProxyRequestEvent request,
            final Context context) {
        try {
            final String body = request.getBody();
            if (body == null || body.isBlank()) {
                return successResponse();
            }

            final TelegramApi.Update update = telegramApi.parseUpdate(body);
            if (update == null) {
                return successResponse(); // Not a message update
            }

            // Security check: only allow the admin user
            if (!securityService.isAuthorized(update.chatId())) {
                context.getLogger().log("Unauthorized access attempt from chatId: " + update.chatId());
                return successResponse(); // Silently ignore unauthorized users
            }

            // Route based on message content
            if (update.isStartCommand()) {
                handleStartCommand(update.chatId());
            } else if (!update.isCommand()) {
                handleTextMessage(update.chatId(), update.text());
            }
            // Ignore other commands for now

            return successResponse();

        } catch (Exception e) {
            context.getLogger().log("Error processing request: " + e.getMessage());
            return successResponse(); // Always return 200 to Telegram to avoid retries
        }
    }

    private void handleStartCommand(final long chatId) throws IOException, InterruptedException {
        telegramApi.sendMessage(chatId, "Hello! I am ready. Send me a message to save to Drive.");
    }

    private void handleTextMessage(final long chatId, final String text) throws IOException, InterruptedException {
        driveApi.uploadNote(text);
        telegramApi.sendMessage(chatId, "Saved to Drive!");
    }

    private APIGatewayProxyResponseEvent successResponse() {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(200)
                .withBody("{\"ok\":true}");
    }
}
