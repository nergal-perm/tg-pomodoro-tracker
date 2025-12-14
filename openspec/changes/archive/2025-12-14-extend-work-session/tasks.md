# Implementation Tasks

- [x] Update `SessionState` enum to include `WAITING_FOR_EXTENSION`.
- [x] Update `BotHandler` `handleTimerDone` to transition to `WAITING_FOR_EXTENSION` and show options.
- [x] Add `EXTENSION_BUTTONS` constant to `BotHandler`.
- [x] Implement callback handling for `extend:<minutes>`:
    - [x] Create new timer.
    - [x] Update session with new schedule name and state `WORKING`.
    - [x] Send confirmation message.
- [x] Implement callback handling for `finish` (or reuse existing logic moved to a handler):
    - [x] Transition to `WAITING_FOR_ENERGY`.
    - [x] Trigger reflection flow.
- [x] Verify `SessionData` updates preserve `startTime` and `duration` (initial) correctly.
- [x] Update tests to cover the new state transition and extension flow.
