package ewc.exocortex.pomodoro;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.UserCredentials;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;

/**
 * Handles uploading Markdown notes to Google Drive.
 */
public final class DriveService implements DriveApi {

    private static final String APPLICATION_NAME = "Pomodoro Bot";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final DateTimeFormatter FILENAME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmmss");

    private final Drive drive;
    private final String folderId;

    public DriveService(final Drive drive, final String folderId) {
        this.drive = drive;
        this.folderId = folderId;
    }

    /**
     * Creates a DriveService using environment variables.
     * Expects:
     * - GOOGLE_CLIENT_ID
     * - GOOGLE_CLIENT_SECRET
     * - GOOGLE_REFRESH_TOKEN
     * - GOOGLE_DRIVE_FOLDER_ID
     */
    public static DriveService fromEnvironment() throws IOException, GeneralSecurityException {
        final String clientId = System.getenv("GOOGLE_CLIENT_ID");
        final String clientSecret = System.getenv("GOOGLE_CLIENT_SECRET");
        final String refreshToken = System.getenv("GOOGLE_REFRESH_TOKEN");
        final String folderId = System.getenv("GOOGLE_DRIVE_FOLDER_ID");

        if (clientId == null || clientId.isBlank()) {
            throw new IllegalStateException("GOOGLE_CLIENT_ID environment variable is not set");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("GOOGLE_CLIENT_SECRET environment variable is not set");
        }
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalStateException("GOOGLE_REFRESH_TOKEN environment variable is not set");
        }
        if (folderId == null || folderId.isBlank()) {
            throw new IllegalStateException("GOOGLE_DRIVE_FOLDER_ID environment variable is not set");
        }

        final HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();

        final GoogleCredentials credentials = UserCredentials.newBuilder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRefreshToken(refreshToken)
                .build();

        final Drive drive = new Drive.Builder(httpTransport, JSON_FACTORY, new HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build();

        return new DriveService(drive, folderId);
    }

    @Override
    public String uploadNote(final String content) throws IOException {
        final String fileName = "note_" + LocalDateTime.now().format(FILENAME_FORMAT) + ".md";

        final File fileMetadata = new File();
        fileMetadata.setName(fileName);
        fileMetadata.setMimeType("text/markdown");
        fileMetadata.setParents(Collections.singletonList(folderId));

        final com.google.api.client.http.ByteArrayContent mediaContent = new com.google.api.client.http.ByteArrayContent(
                "text/markdown",
                content.getBytes(StandardCharsets.UTF_8));

        final File file = drive.files()
                .create(fileMetadata, mediaContent)
                .setFields("id")
                .execute();

        return file.getId();
    }
}
