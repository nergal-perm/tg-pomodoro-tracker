## 1. Cleanup
- [ ] 1.1 Remove `DriveService.java` and `DriveApi.java`.
- [ ] 1.2 Remove Google Drive dependencies from `pom.xml`.
- [x] 1.3 Remove `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, etc., from environment variable parsing.

## 2. Ingestion Implementation
- [x] 2.1 Update `SessionData` model to remove `energyLevel`, `focusLevel`, `qualityScore`.
- [x] 2.2 Implement `DynamoDbIngestionService` to put items into the `IngestionTable` (shared with MCP).
- [x] 2.3 Update `BotHandler` to use the new ingestion service on session completion.

## 3. Bot Logic Simplification
- [x] 3.1 Remove the "Twist" questions asking for Energy/Focus.
- [x] 3.2 Simplify the post-work flow to only ask for "Outcome/Reflection".

## 4. Verification
- [x] 4.1 Test that finished sessions appear in DynamoDB.
- [x] 4.2 Verify the Bot no longer crashes on missing Drive credentials.
