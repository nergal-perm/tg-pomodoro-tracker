# Change: Update Session Flow with Ritual and Reflection

## Why
To enhance focus and gather structured data about work sessions, the user requires a comprehensive "Ritual" (pre-work questions) and "Twist" (post-work reflection). Additionally, the bot must communicate entirely in Russian to match the user's primary language.

## What Changes
- **Localization**: All bot responses will be in Russian.
- **Pre-work Ritual**: Before the timer starts, the bot will ask 7 specific questions (Task, Role, Product, Usage, Context, Resources, Constraints) instead of just a simple title.
- **Post-work Reflection**: After the timer ends, the bot will ask 5 specific questions (Energy, Focus, Quality, Summary, Next Step) instead of a simple result prompt.
- **Data Persistence**: All answers from the Ritual and Reflection phases will be saved into the generated Markdown note.

## Impact
- **Specs**:
    - `bot-core`: Language updates.
    - `work-session-management`: Significant state machine expansion for multi-step questionnaires.
- **Code**:
    - `BotHandler`: Needs to handle new states (`WAITING_FOR_ROLE`, `WAITING_FOR_ENERGY`, etc.) and flow logic.
    - `SessionState`: Needs to store answers for all new questions.
    - `DriveService`: Needs to format the final Markdown note with the new data fields (template to be provided).
