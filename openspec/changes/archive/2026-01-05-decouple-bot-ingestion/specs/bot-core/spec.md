## REMOVED Requirements
### Requirement: Archive to Drive
**Reason**: Replaced by DynamoDB ingestion to decouple the bot from the vault file system and support serverless deployment.
**Migration**: Existing code will be removed. No data migration needed (clean cut).

## ADDED Requirements
### Requirement: Session Ingestion
The bot MUST persist the completed session data as a raw record in the configured ingestion storage (DynamoDB).

#### Scenario: Ingest Session
- **WHEN** the session is completed and all required inputs are collected
- **THEN** the bot writes a JSON representation of the `SessionData` to the `IngestionTable`.
- **AND** the bot replies "Сессия сохранена. Отдыхаем."
