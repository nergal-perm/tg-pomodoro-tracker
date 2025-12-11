# Change: Init Walking Skeleton

## Why
To establish the foundational infrastructure and prove the integration between AWS Lambda, Telegram API, and Google Drive API. This "walking skeleton" ensures the deployment pipeline and core permissions are correctly configured before introducing complex business logic like the Pomodoro timer or state machine.

## What Changes
- Initialize the Java 21 Maven project structure.
- Implement a monolithic AWS Lambda handler (`BotHandler`).
- Implement basic Telegram Webhook processing for `/start` and text messages.
- Implement Google Drive authentication and file creation.
- Enforce strict single-user access control (hardcoded ID).

## Impact
- **Affected Specs**: `bot-core` (New)
- **Affected Code**: Project root (new Maven project), `src/main/java`
- **Infrastructure**: Requires setup of API Gateway, Lambda, and Secrets Manager for Google Credentials.
