package ewc.exocortex.pomodoro;

import java.io.IOException;
import java.util.List;

/**
 * Interface for Telegram API interactions.
 */
public interface TelegramApi {

    /**
     * Parses the incoming Webhook JSON into a simplified Update record.
     *
     * @param json the raw JSON body from Telegram
     * @return the parsed Update, or null if not a message or callback update
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
     * Sends a message with inline keyboard buttons.
     *
     * @param chatId  the target chat ID
     * @param text    the message text
     * @param buttons list of button options (text = displayed, callbackData =
     *                returned on click)
     * @throws IOException          if the API call fails
     * @throws InterruptedException if interrupted
     */
    void sendMessageWithKeyboard(long chatId, String text, List<Button> buttons)
            throws IOException, InterruptedException;

    /**
     * Answers a callback query to remove the "loading" state from button.
     *
     * @param callbackQueryId the callback query ID from the update
     * @throws IOException          if the API call fails
     * @throws InterruptedException if interrupted
     */
    void answerCallbackQuery(String callbackQueryId) throws IOException, InterruptedException;

    /**
     * Represents a parsed Telegram Update.
     */
    record Update(long chatId, String text, String callbackQueryId, String callbackData) {
        public boolean isCommand() {
            return text != null && text.startsWith("/");
        }

        public boolean isStartCommand() {
            return "/start".equals(text) || (text != null && text.startsWith("/start "));
        }

        public boolean isStopCommand() {
            return "/stop".equals(text);
        }

        public boolean isCallbackQuery() {
            return callbackQueryId != null && callbackData != null;
        }

        public boolean isTextMessage() {
            return text != null && !text.isEmpty() && callbackQueryId == null;
        }
    }

    /**
     * Represents a button for inline keyboard.
     */
    record Button(String text, String callbackData) {
    }
}
