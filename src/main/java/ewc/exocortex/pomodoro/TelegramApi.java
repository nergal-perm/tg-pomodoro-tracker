package ewc.exocortex.pomodoro;

import java.io.IOException;

/**
 * Interface for Telegram API interactions.
 */
public interface TelegramApi {

    /**
     * Parses the incoming Webhook JSON into a simplified Update record.
     *
     * @param json the raw JSON body from Telegram
     * @return the parsed Update, or null if not a message update
     * @throws IOException if parsing fails
     */
    Update parseUpdate(String json) throws IOException;

    /**
     * Sends a text message to the specified chat.
     *
     * @param chatId the target chat ID
     * @param text   the message text
     * @throws IOException          if the API call fails
     * @throws InterruptedException if interrupted
     */
    void sendMessage(long chatId, String text) throws IOException, InterruptedException;

    /**
     * Represents a parsed Telegram Update.
     */
    record Update(long chatId, String text) {
        public boolean isCommand() {
            return text != null && text.startsWith("/");
        }

        public boolean isStartCommand() {
            return "/start".equals(text) || (text != null && text.startsWith("/start "));
        }
    }
}
