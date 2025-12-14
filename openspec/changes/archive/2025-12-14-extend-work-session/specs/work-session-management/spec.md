# Work Session Management Spec Delta

## MODIFIED Requirements

### Requirement: Session Lifecycle
The bot MUST manage the session timer and handle completion by offering an extension choice, or interruption by initiating the reflection phase.

#### Scenario: Timer expires
- **Given** bot is in `WORKING` state
- **When** scheduled timer event triggers
- **Then** bot sends "Время вышло. Что делаем дальше?" with buttons:
  - "Завершить"
  - "+5 мин", "+10 мин", "+15 мин", "+20 мин", "+30 мин"
- **And** bot state transitions to `WAITING_FOR_EXTENSION`

## ADDED Requirements

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
