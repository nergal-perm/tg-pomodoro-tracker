# Project Context

## Purpose
To create a serverless, personal Telegram bot for Pomodoro tracking that enforces a reflection phase ("The Twist"). The bot manages work sessions using AWS EventBridge, interrupts the user upon completion to collect reflection details (e.g., work summary, energy level), and archives these details as formatted Markdown files in Google Drive. The final set of collected details will be significantly expanded. The system is designed to be cost-effective, low-latency, and event-driven.

## Tech Stack
- **Language**: Java 21 (Amazon Corretto)
- **Runtime**: AWS Lambda with **SnapStart** enabled
- **Build Tool**: Maven
- **Infrastructure as Code**: TBD (AWS CDK or AWS SAM)
- **Cloud Components**:
  - **Compute**: AWS Lambda (Monolithic Handler)
  - **API**: AWS API Gateway (HTTP API)
  - **State**: Amazon DynamoDB (Enhanced Client)
  - **Scheduling**: Amazon EventBridge Scheduler
  - **Config**: AWS Secrets Manager
- **External Integration**:
  - Telegram Bot API (Webhooks)
  - Google Drive API (v3)

## Project Conventions

### Code Style
- **Monolithic Handler**: A single entry point (`BotHandler`) routes events from both Telegram (User commands) and EventBridge (Timer triggers).
- **Service Layering**: Logic is separated into domain services:
  - `TelegramService`: Handling updates, keypads, and messaging.
  - `TimerService`: Managing EventBridge schedules.
  - `DriveService`: Handling Google Drive authentication and file uploads.
- **Type Safety**: Strong usage of Java types for domain models (e.g., `PomodoroState`, `Update`).

### Architecture Patterns
- **No-Idle Compute**: Uses EventBridge Scheduler instead of indefinite `Thread.sleep()` or Step Functions for timing, ensuring zero cost during the 25-minute work blocks.
- **Intermediate State Persistence**: Session state for the ongoing session is persisted in DynamoDB (e.g., `IDLE` -> `WORKING` -> `WAITING_FOR_INPUT`).  
- **Personal Security**: Hardcoded `chatId` verification at the entry point to restrict usage to the owner.

### Testing Strategy
- **Framework**: JUnit 5.
- **Philosophy**: "Testing without Mocks". Focus on state-based testing and real I/O simulation.
- **Tools**: Use custom library `testable-io` for abstracting and testing I/O interactions without traditional mocking frameworks.

### Git Workflow
- **Strategy**: Trunk-based development.
- **Commits**: Conventional Commits standard (e.g., `feat:`, `fix:`, `chore:`).

## Domain Context
- **The "Ritual"**: Before the Pomodoro timer starts, the bot will ask the user a series of questions about the upcoming work session to help them focus and set intentions.
- **The "Twist"**: The core value proposition is the *active interruption* at the end of a session, requiring user input before returning to `IDLE`.
- **States**: The bot's state machine is driven by the user's interaction flow and the questions asked during the "Ritual" and "Twist" phases. Examples include:
  - `IDLE`: Ready for `/start`.
  - `WORKING`: Timer running.
  - `WAITING_FOR_WORK_LOG`: Timer finished, waiting for text input.
  - `WAITING_FOR_ENERGY`: Work log received, waiting for button selection.
- **Artifacts**: The final output is a Markdown file in Google Drive containing the work session's metadate along with any info entered by the user during the "Ritual" and "Twist" phases. 

## Important Constraints
- **Cold Starts**: Must use **SnapStart** to ensure the bot feels responsive (sub-second latency) despite being a Java Lambda.
- **Cost Optimization**: Architecture chosen specifically to stay within AWS Free Tier limits where possible.
- **Single User**: No multi-tenancy support required; specific to the user's Telegram ID.

## External Dependencies
- **Telegram**: Bot Token, Webhook setup.
- **Google**: Service Account JSON credentials, shared folder access.
