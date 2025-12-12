# Design: Interactive Work Session Flow

## State Machine
stateDiagram
    [*] --> IDLE
    IDLE --> WAITING_FOR_DURATION: /start
    WAITING_FOR_DURATION --> WAITING_FOR_TITLE: Select Duration
    WAITING_FOR_TITLE --> WORKING: Enter Title (Starts Timer)
    WORKING --> WAITING_FOR_RESULT: Timer Fires OR /stop
    WAITING_FOR_RESULT --> IDLE: Enter Result (Saves Note)

## Data Model (DynamoDB)
The `PomodoroBotState` table needs to store session context to persist across Lambda invocations.

| Attribute      | Type             | Description                                        |
| :------------- | :--------------- | :------------------------------------------------- |
| `status`       | String           | Current state enum.                                |
| `startTime`    | String (ISO8601) | When the session timer started.                    |
| `duration`     | Integer          | Selected duration in minutes.                      |
| `sessionTitle` | String           | User-provided title for the session.               |
| `scheduleName` | String           | ID of the EventBridge schedule (for cancellation). |

## File Output Format
**Filename**: `YYYY-MM-DD <Session Title>.md`

**Content**:
```markdown
---
started: <ISO8601 Start Time>
stopped: <ISO8601 Stop Time>
timer_setting: <Duration in Minutes>
---

<User Result Description>
```

## Interaction Flow
1. **User**: `/start`
2. **Bot**: "How long do you want to work?" [30] [45] [60] [90] [5]
3. **User**: Clicks [45]
4. **Bot**: "Duration set to 45m. What are you working on?"
5. **User**: "Refactoring Login"
6. **Bot**: "Timer started for 'Refactoring Login'. Focus!" (Schedules EventBridge)
7. **(Time Passes)** -> EventBridge triggers Lambda
8. **Bot**: "Time's up! Tell me what you have done."
9. **User**: "Implemented OAuth flow."
10. **Bot**: (Saves file `2025-10-10 Refactoring Login.md`) "Session saved."
