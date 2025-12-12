# Spec: Work Session Management

## ADDED Requirements

### Requirement: Interactive Session Setup
The bot MUST provide an interactive way to configure the session duration and title.

#### Scenario: User initiates session
- **Given** bot is in `IDLE` state OR bot is not initialized
- **When** user sends `/start`
- **Then** bot replies asking for duration with inline buttons (30, 45, 60, 90, 5 mins)
- **And** bot state transitions to `WAITING_FOR_DURATION`

#### Scenario: User selects duration
- **Given** bot is in `WAITING_FOR_DURATION` state
- **When** user clicks a duration button (e.g., "45")
- **Then** bot saves duration 45 to session state
- **And** bot asks for session title
- **And** bot state transitions to `WAITING_FOR_TITLE`

#### Scenario: User provides title
- **Given** bot is in `WAITING_FOR_TITLE` state
- **When** user sends title text "My Task"
- **Then** bot saves "My Task" as title
- **And** bot schedules timer event for 45 minutes
- **And** bot replies confirming timer started
- **And** bot state transitions to `WORKING`

### Requirement: Session Lifecycle
The bot MUST manage the session timer and handle completion or interruption.

#### Scenario: Timer expires
- **Given** bot is in `WORKING` state
- **When** scheduled timer event triggers
- **Then** bot sends "Time's up, tell me what have you done"
- **And** bot state transitions to `WAITING_FOR_RESULT`

#### Scenario: User stops session manually
- **Given** bot is in `WORKING` state
- **When** user sends `/stop`
- **Then** bot cancels scheduled timer
- **And** bot sends "Stopped... what did you do?"
- **And** bot state transitions to `WAITING_FOR_RESULT`

### Requirement: Result Archiving
The bot MUST save the session outcome to a Markdown file.

#### Scenario: User saves session result
- **Given** bot is in `WAITING_FOR_RESULT` state
- **When** user sends result text
- **Then** bot generates Markdown note with:
  - Filename including date and session title
  - Frontmatter with `time started`, `time stopped`, `timer setting`
  - Content with result description
- **And** bot uploads note to Google Drive
- **And** bot replies confirming save
- **And** bot state transitions to `IDLE`
