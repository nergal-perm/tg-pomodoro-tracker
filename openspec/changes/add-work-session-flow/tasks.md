# Implementation Tasks

1. **State Machine & Data Model**
   - [x] Define `SessionState` enum with states: `IDLE`, `WAITING_FOR_DURATION`, `WAITING_FOR_TITLE`, `WORKING`, `WAITING_FOR_RESULT`.
   - [x] Define DynamoDB entity schema to store `chatId` (PK), `status`, `sessionTitle`, `duration`, `startTime`, `scheduleName`.

2. **Command Handling (`/start`)**
   - [x] Modify `/start` handler to transition to `WAITING_FOR_DURATION`.
   - [x] Implement response with Inline Keyboard (5, 30, 45, 60, 90 mins).
   - [x] Handle callback queries for duration selection.

3. **Session Setup Flow**
   - [x] Implement transition from Duration Selection -> Title Prompt.
   - [x] Implement Title Input handler -> Start Timer logic.
   - [x] Integrate with `TimerService` to schedule EventBridge event.

4. **Session Completion Flow**
   - [x] Implement `TIMER_DONE` event handler (EventBridge trigger).
   - [x] Implement `/stop` command handler (transitions to result prompt).
   - [x] Verify both paths transition to `WAITING_FOR_RESULT`.

5. **Result Processing & Storage**
   - [x] Implement text handler for `WAITING_FOR_RESULT`.
   - [x] Format Markdown content (Frontmatter + Body).
   - [x] Integrate `DriveService` to upload file.
   - [x] Cleanup state (return to `IDLE`).
