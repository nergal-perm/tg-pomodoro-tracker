# bot-core Specification

## Purpose
Provides the foundational Telegram bot infrastructure including single-user authorization, basic command handling, and Google Drive integration for saving notes.
## Requirements
### Requirement: Single User Authorization
The bot MUST restrict access to a single authorized user identified by their Telegram Chat ID.

#### Scenario: Authorized User
- **WHEN** a message is received from the configured `ADMIN_CHAT_ID`
- **THEN** the bot processes the request.

#### Scenario: Unauthorized User
- **WHEN** a message is received from an unknown Chat ID
- **THEN** the bot ignores the request entirely (no reply sent).

### Requirement: Interactive Greeting
The bot MUST respond to the start command in Russian to confirm availability.

#### Scenario: Start Command
- **WHEN** the authorized user sends `/start`
- **THEN** the bot replies with "Привет! Чем займешься в этой рабочей сесии?"

### Requirement: Session Ingestion
The bot MUST persist the completed session data as a raw record in the configured ingestion storage (DynamoDB).

#### Scenario: Ingest Session
- **WHEN** the session is completed and all required inputs are collected
- **THEN** the bot writes a JSON representation of the `SessionData` to the `IngestionTable`.
- **AND** the bot replies "Сессия сохранена. Отдыхаем."

