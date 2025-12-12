# Implementation Tasks

1. **State Machine & Data Model**
   - [ ] Define `PomodoroState` enum with states: `IDLE`, `WAITING_FOR_DURATION`, `WAITING_FOR_TITLE`, `ACTIVE`, `WAITING_FOR_RESULT`.
   - [ ] Define DynamoDB entity schema to store `chatId` (PK), `status`, `sessionTitle`, `duration`, `startTime`, `scheduleName`.

2. **Command Handling (`/start`)**
   - [ ] Modify `/start` handler to check `IDLE` state.
   - [ ] Implement response with Inline Keyboard (30, 45, 60, 90, 5 mins).
   - [ ] Handle callback queries for duration selection.

3. **Session Setup Flow**
   - [ ] Implement transition from Duration Selection -> Title Prompt.
   - [ ] Implement Title Input handler -> Start Timer logic.
   - [ ] Integrate with `TimerService` to schedule EventBridge event.

4. **Session Completion Flow**
   - [ ] Implement `TIMER_DONE` event handler (EventBridge trigger).
   - [ ] Implement `/stop` command handler (Cancel Timer + Prompt Report).
   - [ ] Verify both paths transition to `WAITING_FOR_RESULT`.

5. **Result Processing & Storage**
   - [ ] Implement text handler for `WAITING_FOR_RESULT`.
   - [ ] Format Markdown content (Frontmatter + Body).
   - [ ] Integrate `DriveService` to upload file.
   - [ ] Cleanup state (return to `IDLE`).
