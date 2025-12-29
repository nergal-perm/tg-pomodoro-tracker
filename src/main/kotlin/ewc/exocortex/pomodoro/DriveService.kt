package ewc.exocortex.pomodoro

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.UserCredentials
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException

/**
 * Handles uploading Markdown notes to Google Drive.
 */
class DriveService(private val drive: Drive, private val folderId: String?) : DriveApi {
    @Throws(IOException::class)
    override fun uploadNote(fileName: String?, content: String): String? {
        println("DriveService: Preparing to upload file: $fileName to folder: $folderId")

        val fileMetadata = File()
        fileMetadata.setName(fileName)
        fileMetadata.setMimeType("text/markdown")
        fileMetadata.setParents(mutableListOf(folderId))

        val mediaContent = ByteArrayContent(
            "text/markdown",
            content.toByteArray(StandardCharsets.UTF_8)
        )

        try {
            val file = drive.files()
                .create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()

            println("DriveService: Successfully uploaded file. ID: ${file.id}")
            return file.id
        } catch (e: GoogleJsonResponseException) {
            System.err.println("DriveService: Google API Error: ${e.statusCode} ${e.statusMessage}")
            System.err.println("DriveService: Error Details: ${e.details}")
            throw e
        } catch (e: IOException) {
            System.err.println("DriveService: IO Error during upload: ${e.message}")
            throw e
        }
    }

    companion object {
        private const val APPLICATION_NAME = "Pomodoro Bot"
        private val JSON_FACTORY: JsonFactory = GsonFactory.getDefaultInstance()

        /**
         * Creates a DriveService using environment variables.
         * Expects:
         * - GOOGLE_CLIENT_ID
         * - GOOGLE_CLIENT_SECRET
         * - GOOGLE_REFRESH_TOKEN
         * - GOOGLE_DRIVE_FOLDER_ID
         */
        @Throws(IOException::class, GeneralSecurityException::class)
        fun fromEnvironment(): DriveService {
            val clientId = safeGetFromEnvironment("GOOGLE_CLIENT_ID")
            val clientSecret = safeGetFromEnvironment("GOOGLE_CLIENT_SECRET")
            val refreshToken = safeGetFromEnvironment("GOOGLE_REFRESH_TOKEN")
            val folderId = safeGetFromEnvironment("GOOGLE_DRIVE_FOLDER_ID")


            val credentials = UserCredentials.newBuilder()
                .setClientId(clientId)
                .setClientSecret(clientSecret)
                .setRefreshToken(refreshToken)
                .build()
                .createScoped(listOf(DriveScopes.DRIVE_FILE))

            // Pre-flight check: try to refresh the token immediately to catch invalid_grant
            // early
            try {
                println("DriveService: Performing pre-flight token refresh check...")
                credentials.refreshAccessToken()
                println("DriveService: Token refresh check successful.")
            } catch (e: IOException) {
                val message = e.message
                if (message != null && message.contains("invalid_grant")) {
                    throw IOException(
                        "Google OAuth2 Error: invalid_grant. The refresh token has expired or been revoked. " +
                                "If your GCP project is in 'Testing' mode, tokens expire in 7 days. " +
                                "Please change status to 'In Production' in GCP Console and generate a new refresh token.",
                        e
                    )
                }
                throw IOException("Failed to refresh Google OAuth2 token during initialization: $message", e)
            }

            val httpTransport: HttpTransport = GoogleNetHttpTransport.newTrustedTransport()
            val drive = Drive.Builder(httpTransport, JSON_FACTORY, HttpCredentialsAdapter(credentials))
                .setApplicationName(APPLICATION_NAME)
                .build()

            return DriveService(drive, folderId)
        }

        private fun safeGetFromEnvironment(envName: String): String {
            val envValue = System.getenv(envName)
            check(!(envValue == null || envValue.isBlank())) { "$envName environment variable is not set" }
            return envValue
        }
    }
}