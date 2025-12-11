# I want to create telegram bot that will provide Pomodoro tracking functionality, but with a twist - it should remind about the end of the session, ask user some questions about its result and work product, and then save the note (the markdown file) to my Google Drive folder. I would like to make it serverless, probably on AWS Lambda if it is possible.

Create the implementation plan for such a bot. Ask me any questions about the functionality and interactions with user

Here is the implementation plan for your serverless Pomodoro bot on AWS.

### **Serverless Pomodoro Bot Implementation Plan**

This architecture is designed to be fully event-driven and cost-effective, leveraging AWS Lambda's ephemeral nature while solving the "25-minute timer" problem without paying for idle compute time.

#### **1. High-Level Architecture**

* **Entry Point**: AWS API Gateway (HTTP API) receiving Webhooks from Telegram.
* **Compute**: AWS Lambda (Handling logic, API calls, and flow control).
* **State Management**: Amazon DynamoDB (Storing active session state, user answers, and conversation context).
* **Timer Mechanism**: Amazon EventBridge Scheduler (Handling the 25-minute delay).
* **Storage**: Google Drive API (Saving the final markdown file).


#### **2. Core Workflow**

**Phase 1: The Start**

1. **User** sends `/start_work` via Telegram.
2. **API Gateway** triggers the `BotHandler` Lambda.
3. **Lambda**:
    * Creates a session record in **DynamoDB** (Status: `WORKING`, StartTime: `Now`).
    * Creates a **one-time schedule** in **EventBridge Scheduler** set to trigger in 25 minutes.
    * Responds to User: "Timer started! Focus now."

**Phase 2: The Reminder (The "Twist")**

1. **EventBridge Scheduler** triggers the `SessionEnd` Lambda at the designated time.
2. **Lambda**:
    * Updates DynamoDB state to `WAITING_FOR_INPUT`.
    * Sends a Telegram message: "Time is up! What did you accomplish this session?"

**Phase 3: The Interrogation \& Save**

1. **User** replies with text (e.g., "Refactored the login module").
2. **Lambda** (triggered by Webhook):
    * Checks DynamoDB state. Sees `WAITING_FOR_INPUT`.
    * *Optional Loop*: If you have multiple questions, it cycles through them, updating the state (e.g., `WAITING_FOR_ENERGY_LEVEL`).
    * Once all answers are collected, it formats the data into a Markdown string.
3. **Lambda** connects to **Google Drive API**:
    * Authenticates via Service Account (stored in AWS Secrets Manager).
    * Creates a `.md` file in the specific folder.
4. **Lambda** sends final confirmation: "Session saved to Drive. Take a break!"

#### **3. Technical Stack Recommendations**

* **Runtime**: **Java 21 (SnapStart enabled)** or **Python 3.12**.
    * *Note*: As a Java expert, you might prefer Java. With **SnapStart**, cold starts are negligible. Python is lighter if you want a quick script, but Java offers better type safety for the Drive API interactions.
* **Infrastructure as Code**: AWS CDK (Java) or SAM.
* **Database Schema (DynamoDB)**:
    * PK: `ChatId` (String)
    * SK: `SessionId` (ULID/UUID) or just `CURRENT` for simple state tracking.
    * Attributes: `State` ("IDLE", "WORKING", "QA_1", "QA_2"), `Buffer` (Map/List of answers).


#### **4. Key AWS Services Configuration**

* **EventBridge Scheduler**: Use the `CreateSchedule` API. It is superior to standard EventBridge Rules or Step Functions for this specific case because it allows precision timing without managing a state machine execution history.
* **Google Drive Auth**:
    * Create a Google Cloud Service Account.
    * Download the JSON key.
    * Store the JSON key in **AWS Secrets Manager**.
    * Share your target Google Drive folder with the Service Account's email address (editor access).

***

### **Clarifying Questions**

To finalize the design and code structure, I need a few details:

1. **The "Twist" Logic**: Do you want the questions to be hardcoded (e.g., always "What did you do?" and "How do you feel?") or do you want to use an LLM (like Gemini or GPT-4o) to generate dynamic follow-up questions based on your first answer?
2. **User Access**: Is this a personal bot just for you (hardcoded Telegram User ID security), or should it handle multiple users (requires a "Register" flow or database separation)?
3. **Tech Preference**: Given your background, would you prefer the Lambda implementation in **Java** (Spring Cloud Function / Micronaut) to align with your expertise, or **Python** for brevity?
4. **Handling Interruptions**: If you finish early, should there be a `/stop` command that cancels the EventBridge timer and jumps straight to the questions?