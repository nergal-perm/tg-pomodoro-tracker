package ewc.exocortex.pomodoro;

import java.io.IOException;

/**
 * Interface for Google Drive file operations.
 */
public interface DriveApi {

    /**
     * Uploads a Markdown note to Google Drive.
     *
     * @param fileName the name for the file
     * @param content  the Markdown content to save
     * @return the file ID of the created file
     * @throws IOException if the upload fails
     */
    String uploadNote(String fileName, String content) throws IOException;
}
