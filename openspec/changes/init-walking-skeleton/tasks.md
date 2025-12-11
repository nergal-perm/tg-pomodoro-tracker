## 1. Project Setup
- [ ] 1.1 Initialize Maven project with Java 21 and AWS Lambda SnapStart configuration.
- [ ] 1.2 Add dependencies: AWS Lambda Events, Google Drive API v3, Jackson, JUnit 5, `testable-io`.
- [ ] 1.3 Create `openspec/project.md` based structure (package `ewc.exocortex.pomodoro`).

## 2. Core Implementation
- [ ] 2.1 Implement `SecurityService`: Verify incoming `chatId` against environment variable/hardcoded ID.
- [ ] 2.2 Implement `DriveService`: Authenticate with Google Service Account and provide `uploadNote(String content)` method.
- [ ] 2.3 Implement `TelegramService`: Parse `Update` JSON, handle `/start` command, and send `sendMessage` reply.
- [ ] 2.4 Implement `BotHandler`: Main Lambda entry point routing Webhook events.

## 3. Testing
- [ ] 3.1 Write unit tests for `SecurityService` (Allow/Deny scenarios).
- [ ] 3.2 Write unit tests for `BotHandler` routing logic using `testable-io`.

## 4. Deployment Config
- [ ] 4.1 Create `template.yaml` (AWS SAM) or `cdk` stack (TBD) references (Note: We will start with a basic `template.yaml` for SAM as a default for serverless projects unless otherwise specified).
