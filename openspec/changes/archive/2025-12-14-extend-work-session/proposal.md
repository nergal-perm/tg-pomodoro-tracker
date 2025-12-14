# Proposal: Extend Work Session

**Change ID**: `extend-work-session`

## Goal
Allow users to extend a work session after the initial timer expires, instead of immediately forcing the reflection phase. This provides flexibility for flow states while maintaining the structure of the Pomodoro technique (or deep work sessions).

## Requirements
1. **Interactive Choice**: Upon timer completion, present options to either "Finish" or "Extend" (5, 10, 15, 20, 30 mins).
2. **State Preservation**: Extending the session must preserve the original session context, including task details and the initial start time found in `SessionData`.
3. **Seamless Continuation**: Choosing to extend should restart the existing or create a new timer for the chosen duration and return the user to the `WORKING` state.
4. **Summary**: Choosing to finish should proceed to the existing reflection flow (`WAITING_FOR_ENERGY`).
