package ewc.exocortex.pomodoro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

/**
 * Handles Telegram API interactions: parsing updates and sending messages.
 */
public final class TelegramService implements TelegramApi {

    private static final String TELEGRAM_API_BASE = "https://api.telegram.org/bot";

    private final String botToken;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TelegramService(final String botToken, final HttpClient httpClient, final ObjectMapper objectMapper) {
        this.botToken = botToken;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a TelegramService using the TELEGRAM_BOT_TOKEN environment variable.
     */
    public static TelegramService fromEnvironment() {
        final String token = System.getenv("TELEGRAM_BOT_TOKEN");
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("TELEGRAM_BOT_TOKEN environment variable is not set");
        }
        return new TelegramService(token, HttpClient.newHttpClient(), new ObjectMapper());
    }

    @Override
    public Update parseUpdate(final String json) throws IOException {
        final JsonNode root = objectMapper.readTree(json);

        // Check for callback query first
        final JsonNode callbackQuery = root.get("callback_query");
        if (callbackQuery != null) {
            final long chatId = callbackQuery.get("message").get("chat").get("id").asLong();
            final String callbackQueryId = callbackQuery.get("id").asText();
            final String callbackData = callbackQuery.get("data").asText();
            return new Update(chatId, null, callbackQueryId, callbackData);
        }

        // Check for regular message
        final JsonNode message = root.get("message");
        if (message == null) {
            return null; // Not a message update
        }

        final long chatId = message.get("chat").get("id").asLong();
        final String text = message.has("text") ? message.get("text").asText() : "";

        return new Update(chatId, text, null, null);
    }

    @Override
    public void sendMessage(final long chatId, final String text) throws IOException, InterruptedException {
        final String url = TELEGRAM_API_BASE + botToken + "/sendMessage";

        final String payload = objectMapper.writeValueAsString(
                Map.of("chat_id", chatId, "text", text));

        sendApiRequest(url, payload);
    }

    @Override
    public void sendMessageWithKeyboard(final long chatId, final String text, final List<Button> buttons)
            throws IOException, InterruptedException {
        final String url = TELEGRAM_API_BASE + botToken + "/sendMessage";

        // Build inline keyboard - one button per row for simplicity
        final List<List<Map<String, String>>> keyboard = buttons.stream()
                .map(b -> List.of(Map.of("text", b.text(), "callback_data", b.callbackData())))
                .toList();

        final Map<String, Object> payload = Map.of(
                "chat_id", chatId,
                "text", text,
                "reply_markup", Map.of("inline_keyboard", keyboard));

        sendApiRequest(url, objectMapper.writeValueAsString(payload));
    }

    @Override
    public void answerCallbackQuery(final String callbackQueryId) throws IOException, InterruptedException {
        final String url = TELEGRAM_API_BASE + botToken + "/answerCallbackQuery";

        final String payload = objectMapper.writeValueAsString(
                Map.of("callback_query_id", callbackQueryId));

        sendApiRequest(url, payload);
    }

    private void sendApiRequest(final String url, final String payload) throws IOException, InterruptedException {
        final HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        final HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Telegram API error: " + response.statusCode() + " - " + response.body());
        }
    }
}
