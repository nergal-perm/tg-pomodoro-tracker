# Change: Init Walking Skeleton

## Why
To establish the foundational infrastructure and prove the integration between AWS Lambda, Telegram API, and Google Drive API. This "walking skeleton" ensures the deployment pipeline and permissions are configured. We will use OAuth2 (User) authentication for Drive to ensure files differ correctly owned by the user, avoiding permission issues with Service Accounts.

## What Changes
- Initialize the Java 21 Maven project structure.
- Implement a monolithic AWS Lambda handler (`BotHandler`).
- Implement basic Telegram Webhook processing for `/start` command and save to Drive.
- Implement Google Drive authentication using **OAuth2 (Refresh Token)**.
- Enforce strict single-user access control.

## Impact
- **Affected Specs**: `bot-core` (New)
- **Affected Code**: Project root (new Maven project), `src/main/java`
- **Infrastructure**: Requires setup of API Gateway, Lambda, and Secrets Manager for Google Credentials.
