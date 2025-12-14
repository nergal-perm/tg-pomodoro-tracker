# Design: Extend Work Session

## Interaction Flow
1. **Timer Expires**:
   - **Current**: Bot sends "Time's up. How was energy?" -> State `WAITING_FOR_ENERGY`.
   - **New**: Bot sends "Time's up. What's next?" with buttons:
     - `[ Finish ]`
     - `[ +5m ]` `[ +10m ]` `[ +15m ]`
     - `[ +20m ]` `[ +30m ]`
   - State becomes `WAITING_FOR_EXTENSION`.

2. **User Extends**:
   - User clicks `[ +10m ]`.
   - Bot triggers `timerService.createTimer(chatId, 10)`.
   - Bot updates session:
     - `status`: `WORKING`.
     - `scheduleName`: New schedule ID.
     - `startTime`, `task`, `duration` (initial): Unchanged.
   - Bot sends: "Extended by 10 minutes. Working."

3. **User Finishes**:
   - User clicks `[ Finish ]`.
   - Bot updates session: `status`: `WAITING_FOR_ENERGY`.
   - Bot sends: "How was energy?" (Standard reflection flow).

## Data Model Changes
- **`SessionState`**: Add `WAITING_FOR_EXTENSION`.
- **`SessionData`**: No schema changes required. `startTime` remains the original start time. `duration` remains the initial planned duration.
  - *Note*: We are not explicitly tracking "total duration" in the session state, but since we have `startTime` and the final `stopTime` (when note is generated), the actual total duration is implicitly captured in the final note's timestamp difference if needed (or just `startTime` to `stopTime`). The requirement is to keep "initial timer value" intact, which implies `duration` field should not be updated to the sum.

## Edge Cases
- **Multiple Extensions**: The user can extend multiple times. Each time the timer expires, they get the same choice.
- **Stop Command**: `/stop` during an extended period works as usual (cancels timer, goes to reflection).
