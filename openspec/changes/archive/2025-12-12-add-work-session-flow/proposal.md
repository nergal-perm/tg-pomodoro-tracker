# Proposal: Add Interactive Work Session Flow

## Goal
To implement a robust, interactive workflow for starting, managing, and concluding Pomodoro work sessions. This includes configuring session duration, capturing session intent (title), enforcing time limits via AWS EventBridge, and structured result logging to Google Drive.

## User Scenarios
1. **Starting a Session**: User sends `/start`, chooses a duration (5, 30, 45, 60, 90 mins), and provides a title.
2. **Session Completion (Timer)**: Timer expires, bot notifies user and asks for a result report.
3. **Session Interruption**: User sends `/stop`, bot cancels timer and asks for a result report.
4. **Result Logging**: User provides a summary, bot saves a formatted Markdown note to Google Drive.

## Proposed Changes
### Bot Logic & State Machine
- **Implement a new state machine**: With states such as `IDLE`, `WAITING_FOR_DURATION`, `WAITING_FOR_TITLE`, `WAITING_FOR_RESULT`, `ACTIVE_SESSION`.
- **`/start` command**: Initiates the state machine, moving to `WAITING_FOR_DURATION` and triggering an interactive menu for duration selection.
- **`/stop` command**: Introduces the ability to end a session prematurely, transitioning to the `WAITING_FOR_RESULT` state.

### Infrastructure
- **EventBridge Scheduler**: Dynamic schedule creation based on selected duration.
- **DynamoDB**: New table schema to store session-related information, including session title, duration, start time, and current state.

### Integrations
- **Google Drive**: Logic to formatting and saving the note with specific metadata.
