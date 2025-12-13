## MODIFIED Requirements
### Requirement: Interactive Greeting
The bot MUST respond to the start command in Russian to confirm availability.

#### Scenario: Start Command
- **WHEN** the authorized user sends `/start`
- **THEN** the bot replies with "Привет! Чем займешься в этой рабочей сесии?"

### Requirement: Archive to Drive
The bot MUST save user messages as Markdown files in the configured Google Drive folder and confirm in Russian.

#### Scenario: Save User Response
- **WHEN** the authorized user sends a text message (not a command) AND bot is in `IDLE` state
- **THEN** the bot uploads a new `.md` file to Google Drive using the provided **OAuth2 User Credentials**.
- **AND** the bot replies "Информация о рабочей сессии сохранена на Диск!"
