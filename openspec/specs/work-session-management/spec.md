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

#### Scenario: Timer expires
- **Given** bot is in `WORKING` state
- **When** scheduled timer event triggers
- **Then** bot sends "Время вышло. Что делаем дальше?" with buttons:
  - "Завершить"
  - "+5 мин", "+10 мин", "+15 мин", "+20 мин", "+30 мин"
- **And** bot state transitions to `WAITING_FOR_EXTENSION`

### Requirement: Result Archiving
The bot MUST save the session outcome, including all ritual and reflection data, to a Markdown file.

#### Scenario: User completes reflection
- **Given** bot is in `WAITING_FOR_NEXT_STEP` state
- **When** user answers the final question (Next Step)
- **Then** bot generates a detailed Markdown note using a predefined template (`src/main/resources/templates/session-note.md`) populated with:
  - Session Meta (Duration, Timestamps)
  - Pre-work Ritual Answers (Task, Role, Product, etc.)
  - Post-work Reflection Answers (Energy, Focus, Quality, Summary, Next Step)
- **And** bot uploads note to Google Drive
- **And** bot replies "Сессия сохранена. Отдыхаем."
- **And** bot state transitions to `IDLE`

### Requirement: Post-work Reflection
The bot MUST conduct a post-work reflection (Twist) after the session ends.

#### Scenario: Reflection Flow
- **Given** bot is in `WAITING_FOR_ENERGY`
- **When** user selects Energy level
- **Then** bot asks "Каков был уровень фокуса?" with buttons (3-Предельный, 2-Обычный, 1-Рассеянный)
- **And** bot transitions to `WAITING_FOR_FOCUS`
- **When** user selects Focus level
- **Then** bot asks "Каково качество рабочего продукта?" with buttons (3-Исключительное, 2-Приемлемое, 1-Низкое)
- **And** bot transitions to `WAITING_FOR_QUALITY`
- **When** user selects Quality level
- **Then** bot asks "Подведите краткий итог сессии."
- **And** bot transitions to `WAITING_FOR_SUMMARY`
- **When** user enters Summary
- **Then** bot asks "Каков следующий шаг?"
- **And** bot transitions to `WAITING_FOR_NEXT_STEP`

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

