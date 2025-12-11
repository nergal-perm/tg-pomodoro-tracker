## 1. Project Setup
- [x] 1.1 Initialize Maven project with Java 21 and AWS Lambda SnapStart configuration.
- [x] 1.2 Add dependencies: AWS Lambda Events, Google Drive API v3, Jackson, JUnit 5, `testable-io`.
- [x] 1.3 Create `openspec/project.md` based structure (package `ewc.exocortex.pomodoro`).

## 2. Core Implementation
- [x] 2.1 Implement `SecurityService`: Verify incoming `chatId` against environment variable/hardcoded ID.
- [x] 2.2 Implement `DriveService`: Authenticate with Google Service Account and provide `uploadNote(String content)` method.
- [x] 2.2b Refactor `DriveService`: Switch to OAuth2 with Refresh Token (`UserCredentials`).
- [x] 2.3 Implement `TelegramService`: Parse `Update` JSON, handle `/start` command, and send `sendMessage` reply.
- [x] 2.4 Implement `BotHandler`: Main Lambda entry point routing Webhook events.

## 3. Testing
- [x] 3.1 Write unit tests for `SecurityService` (Allow/Deny scenarios).
- [x] 3.2 Write unit tests for `BotHandler` routing logic using interface-based test doubles.

## 4. Deployment Config
- [x] 4.1 Create `template.yaml` (AWS SAM) with SnapStart enabled.
- [x] 4.2 Update `template.yaml` to use OAuth2 parameters (`ClientId`, `ClientSecret`, `RefreshToken`).
- [x] 4.3 Update `DEPLOYMENT.md` with OAuth2 setup guide.
