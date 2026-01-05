## MODIFIED Requirements
### Requirement: Post-work Reflection
The bot MUST conduct a streamlined post-work reflection after the session ends, focusing only on the outcome.

#### Scenario: Reflection Flow
- **Given** bot is in `WAITING_FOR_EXTENSION` (completed)
- **When** user chooses to finish the session
- **Then** bot asks "Каков результат? (Что сделано + рефлексия)"
- **And** bot transitions to `WAITING_FOR_OUTCOME`
- **When** user enters the Outcome text
- **Then** bot triggers the **Result Archiving** process.

### Requirement: Session Lifecycle
The bot MUST manage the session timer and handle completion by offering an extension choice, or interruption by initiating the reflection phase.

#### Pre-work Ritual:
The system MUST guide the user through a strictly defined initialization sequence before work begins. This sequence MUST consist of:
    1.  **Duration**: Selection of time block (in minutes).
    2.  **Task**: Definition of the specific activity (What are you doing?).
    3.  **Role**: Selection of the cognitive role (Student, Intellectual, Professional, Explorer, Creator).
    4.  **Product**: Definition of the expected output/artifact.
    -   *Note: Previous steps regarding Context, Resources, and Constraints have been removed to reduce friction.*
    -   Completion of these steps transitions the session to `WORKING` state.

#### Scenario: Timer expires
- **Given** bot is in `WORKING` state
- **When** scheduled timer event triggers
- **Then** bot sends "Время вышло. Что делаем дальше?" with buttons:
  - "Завершить"
  - "+5 мин", "+10 мин", "+15 мин", "+20 мин", "+30 мин"
- **And** bot state transitions to `WAITING_FOR_EXTENSION`

#### Scenario: User finishes session
- **Given** bot is in `WAITING_FOR_EXTENSION` state
- **When** user clicks "Завершить"
- **Then** bot initiates the **Post-work Reflection** (asks for Outcome).

### Requirement: Result Archiving
The bot MUST save the session outcome to the ingestion buffer.

#### Scenario: User completes reflection
- **Given** bot is in `WAITING_FOR_OUTCOME` state
- **When** user enters the outcome
- **Then** bot collects all session data (Meta, Ritual, Outcome)
- **And** bot persists the data via the Ingestion mechanism
- **And** bot replies "Сессия сохранена. Отдыхаем."
- **And** bot state transitions to `IDLE`
