package ewc.exocortex.pomodoro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
        final JsonNode message = root.get("message");
        if (message == null) {
            return null; // Not a message update (could be callback, edited message, etc.)
        }

        final long chatId = message.get("chat").get("id").asLong();
        final String text = message.has("text") ? message.get("text").asText() : "";

        return new Update(chatId, text);
    }

    @Override
    public void sendMessage(final long chatId, final String text) throws IOException, InterruptedException {
        final String url = TELEGRAM_API_BASE + botToken + "/sendMessage";

        final String payload = objectMapper.writeValueAsString(
                new SendMessagePayload(chatId, text));

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

    /**
     * Payload for the sendMessage API call.
     */
    private record SendMessagePayload(long chat_id, String text) {
    }
}
