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
The bot MUST respond to the start command to confirm availability.

#### Scenario: Start Command
- **WHEN** the authorized user sends `/start`
- **THEN** the bot replies with "Hello! I am ready. Send me a message to save to Drive."

### Requirement: Archive to Drive
The bot MUST save user messages as Markdown files in the configured Google Drive folder.

#### Scenario: Save User Response
- **WHEN** the authorized user sends a text message (not a command)
- **THEN** the bot uploads a new `.md` file to Google Drive using the provided **OAuth2 User Credentials**.
- **AND** the bot replies "Saved to Drive!"

