# Change: Decouple Bot Ingestion

## Why
The current Telegram Bot is tightly coupled to the physical Vault structure via Google Drive, making it brittle and hard to deploy in serverless environments without persistent mounts. Additionally, the requirement to log subjective metrics (Energy/Focus) creates high friction, reducing protocol compliance.

## What Changes
- **Decouple Ingestion**: The Bot will no longer write to Google Drive. Instead, it will write raw session records to **AWS DynamoDB**.
- **Simplify Schema**: The `SessionData` model will be strictly reduced to objective fields (Time, Title, Outcome) and the free-text reflection. Subjective metrics (Energy, Focus, Quality) are **REMOVED**.
- **Centralize Governance**: The FPF MCP Server will assume responsibility for polling DynamoDB and creating the formatted `Observation` artifacts in the Vault (note: MCP implementation is distinct, but this change prepares the Bot to support it).

## Impact
- **Specs**:
  - `bot-core`: Replaces Google Drive requirements with DynamoDB Ingestion requirements.
  - `work-session-management`: Updates the data model for captured sessions.
- **Code**:
  - Remove `DriveService`, `DriveApi`.
  - Add `DynamoDbIngestionService`.
  - Update `BotHandler` logic.
