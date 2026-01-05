# work-session-management Specification

## Purpose
Manages interactive Pomodoro-style work sessions with configurable duration, session titles, automatic timers via AWS EventBridge, and structured result logging to Google Drive with metadata-rich Markdown notes.
## Requirements
### Requirement: Interactive Session Setup
The bot MUST provide an interactive way to configure the session duration and conduct a pre-work ritual (questionnaire) before starting the timer.

#### Scenario: User initiates session
- **Given** bot is in `IDLE` state
- **When** user sends `/start`
- **Then** bot replies "Выберите продолжительность сессии (минуты):" with inline buttons (30, 45, 60, 90, 5)
- **And** bot state transitions to `WAITING_FOR_DURATION`

#### Scenario: User selects duration
- **Given** bot is in `WAITING_FOR_DURATION` state
- **When** user clicks a duration button (e.g., "45")
- **Then** bot saves duration
- **And** bot asks "Что ты собираешься делать? (Один глагол, описание метода)"
- **And** bot state transitions to `WAITING_FOR_TASK`

#### Scenario: Pre-work Ritual Flow
- **When** user answers the Task question
- **Then** bot asks "В какой роли ты это будешь делать?" with buttons (Ученик, Интеллектуал, Профессионал, Исследователь, Просветитель)
- **And** bot transitions to `WAITING_FOR_ROLE`
- **When** user selects Role or enters text
- **Then** bot asks "Какой рабочий продукт рассчитываешь получить? (Заготовка, код и т.п.)"
- **And** bot transitions to `WAITING_FOR_PRODUCT_TYPE`
- **When** user answers Product Type
- **Then** bot asks "Где и при каких условиях этот рабочий продукт будет применен?"
- **And** bot transitions to `WAITING_FOR_USAGE_CONTEXT`
- **When** user answers Usage Context
- **Then** bot asks "Каков контекст рабочей сессии? (Ситуация, причина, триггер)"
- **And** bot transitions to `WAITING_FOR_CONTEXT`
- **When** user answers Context
- **Then** bot asks "Каковы ресурсы на входе?"
- **And** bot transitions to `WAITING_FOR_RESOURCES`
- **When** user answers Resources
- **Then** bot asks "Ограничения, если есть?"
- **And** bot transitions to `WAITING_FOR_CONSTRAINTS`
- **When** user answers Constraints
- **Then** bot starts the timer
- **And** bot replies "Таймер запущен. Работаем."
- **And** bot state transitions to `WORKING`

### Requirement: Session Lifecycle
The bot MUST manage the session timer and handle completion by offering an extension choice, or interruption by initiating the reflection phase.

#### Pre-work Ritual:
The system MUST guide the user through a strictly defined initialization sequence before work begins. This sequence MUST consist of:
    1.  **Duration**: Selection of time block (in minutes).
    2.  **Task**: Definition of the specific activity (What are you doing?).
    3.  **Role**: Selection of the cognitive role (Student, Intellectual, Professional, Explorer, Enlightener).
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

### Requirement: Post-work Reflection
The bot MUST conduct a streamlined post-work reflection after the session ends, focusing only on the outcome.

#### Scenario: Reflection Flow
- **Given** bot is in `WAITING_FOR_EXTENSION` (completed)
- **When** user chooses to finish the session
- **Then** bot asks "Каков результат? (Что сделано + рефлексия)"
- **And** bot transitions to `WAITING_FOR_OUTCOME`
- **When** user enters the Outcome text
- **Then** bot triggers the **Result Archiving** process.

### Requirement: Session Extension
The bot MUST allow the user to extend the ongoing session with various duration options, resetting the timer but maintaining the session's continuity.

#### Scenario: User extends session
- **Given** bot is in `WAITING_FOR_EXTENSION` state
- **When** user clicks an extension button (e.g., "+10 мин")
- **Then** bot creates a new timer for the selected duration
- **And** bot replies "Таймер продлен на 10 минут. Работаем."
- **And** bot state transitions back to `WORKING`
- **And** the session's **original start time** and **initial duration** remain unchanged to preserve session integrity.

#### Scenario: User finishes session
- **Given** bot is in `WAITING_FOR_EXTENSION` state
- **When** user clicks "Завершить"
- **Then** bot sends "Каков был уровень энергии?" with buttons (5-Пиковый, 4-Потоковый, 3-Функциональный, 2-Упадок, 1-Истощение, 0-Критический)
- **And** bot state transitions to `WAITING_FOR_ENERGY` (proceeding to reflection phase)

