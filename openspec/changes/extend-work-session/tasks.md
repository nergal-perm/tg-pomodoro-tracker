# Implementation Tasks

- [ ] Update `SessionState` enum to include `WAITING_FOR_EXTENSION`.
- [ ] Update `BotHandler` `handleTimerDone` to transition to `WAITING_FOR_EXTENSION` and show options.
- [ ] Add `EXTENSION_BUTTONS` constant to `BotHandler`.
- [ ] Implement callback handling for `extend:<minutes>`:
    - [ ] Create new timer.
    - [ ] Update session with new schedule name and state `WORKING`.
    - [ ] Send confirmation message.
- [ ] Implement callback handling for `finish` (or reuse existing logic moved to a handler):
    - [ ] Transition to `WAITING_FOR_ENERGY`.
    - [ ] Trigger reflection flow.
- [ ] Verify `SessionData` updates preserve `startTime` and `duration` (initial) correctly.
- [ ] Update tests to cover the new state transition and extension flow.
