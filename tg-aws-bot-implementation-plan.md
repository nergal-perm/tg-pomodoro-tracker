### **Implementation Plan: Serverless Pomodoro Bot (Java/AWS)**

This plan details the architecture, state management, and implementation steps for a personal Pomodoro bot that interacts via Telegram, manages time via AWS EventBridge Scheduler, and saves work logs to Google Drive.

***

### **1. Architecture Overview**

We will use a **Single Lambda Function** (Monolithic Handler) pattern. Since this is a low-traffic personal bot, splitting into micro-lambdas (one for start, one for stop, one for callback) adds unnecessary complexity. A single Java function will route logic based on the incoming event type (Telegram Webhook vs. EventBridge Trigger).

#### **Components**

* **Trigger 1 (User)**: API Gateway (HTTP API) $\rightarrow$ Lambda (`BotHandler`).
* **Trigger 2 (Timer)**: EventBridge Scheduler $\rightarrow$ Lambda (`BotHandler`).
* **State Store**: DynamoDB Table (`PomodoroState`).
* **File Store**: Google Drive API.


#### **Sequence of Operations**

1. **Start**: User types `/start 25` $\rightarrow$ Lambda saves state `WORKING`, creates EventBridge Schedule (Self-target with payload `{ "action": "TIMER_DONE" }`).
2. **Stop**: User types `/stop` $\rightarrow$ Lambda deletes EventBridge Schedule, updates state to `REPORTING`, asks "What did you do?".
3. **Timer Ends**: EventBridge fires $\rightarrow$ Lambda updates state to `REPORTING`, sends Telegram msg "Time's up! What did you do?".
4. **Reporting**: User replies "Fixed bug X" $\rightarrow$ Lambda saves text to buffer, asks "Energy Level?".
5. **Finalize**: User clicks "High" $\rightarrow$ Lambda formats Markdown, uploads to Drive, clears state.

***

### **2. Data Model (DynamoDB)**

**Table Name**: `PomodoroBotState`


| PK (`chatId`) | SK                | Attributes                                                                                                                                                                                                                                                                   |
| :------------ | :---------------- | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `123456789`   | `CURRENT_SESSION` | `status` (String): `IDLE` \| `WORKING` \| `WAITING_FOR_WORK_LOG` \| `WAITING_FOR_ENERGY`<br>`scheduleName` (String): "Pomodoro-123456789" (Stored to allow deletion)<br>`startTime` (String): ISO-8601<br>`workLogBuffer` (String): Temporary storage for the user's answer. |


***

### **3. Java Implementation Details**

#### **Tech Stack**

* **Java 21** (Corretto) + **SnapStart** (Crucial for Lambda performance).
* **Build Tool**: Maven.
* **Libraries**:
    * `aws-lambda-java-events`: For APIGatewayProxyRequestEvent.
    * `software.amazon.awssdk:dynamodb-enhanced`: Clean DB access.
    * `software.amazon.awssdk:scheduler`: To create/delete timers.
    * `com.google.apis:google-api-services-drive`: Drive API.
    * `com.fasterxml.jackson.core`: JSON parsing (Telegram updates).


#### **Class Structure**

1. **`Handler`**: The entry point.

```java
public class BotHandler implements RequestHandler<Map<String, Object>, APIGatewayProxyResponseEvent> {
    // Detects if event is from API Gateway (Telegram) or EventBridge (Timer)
    // Routes to TelegramService or TimerService
}
```

2. **`TelegramService`**: Handles incoming updates.
    * Parses commands (`/start`, `/stop`).
    * Parses text replies (Work Log).
    * Parses callback queries (Energy Level buttons).
    * Sends messages via `HttpClient` (No heavy bot library needed).
3. **`TimerService`**: Wraps EventBridge Scheduler.
    * `startTimer(chatId, minutes)`: Creates a "One-time schedule" targeting this same Lambda.
    * `stopTimer(chatId)`: Calls `deleteSchedule`.
4. **`DriveService`**:
    * Loads `credentials.json` from AWS Secrets Manager (or bundled resource if acceptable security-wise for personal use).
    * `uploadNote(content)`: Creates the .md file.

***

### **4. Step-by-Step Build Guide**

#### **Step 1: Google Cloud Setup**

1. Go to **Google Cloud Console** $\rightarrow$ Create Project.
2. Enable **Google Drive API**.
3. Create a **Service Account** $\rightarrow$ Create Key (JSON).
4. **Crucial**: Open the JSON file, find the `client_email`. Go to your personal Google Drive, create a folder named `PomodoroLogs`, and **Share** it with that email (Editor access).
5. Save the JSON file contents for Step 4.

#### **Step 2: DynamoDB Table**

* Create table `PomodoroBotState`.
* Partition Key: `chatId` (String).
* Sort Key: `sk` (String).


#### **Step 3: IAM Role**

Create a Role for your Lambda with permissions:

* `dynamodb:PutItem`, `dynamodb:GetItem`, `dynamodb:UpdateItem` (on your table).
* `scheduler:CreateSchedule`, `scheduler:DeleteSchedule` (Resource: `*` or scoped to specific prefix).
* `iam:PassRole` (Scheduler needs a role to invoke your Lambda).


#### **Step 4: The Lambda Code (Key Logic Snippets)**

**Handling the Timer (EventBridge Scheduler)**

```java
// Create a one-time schedule
CreateScheduleRequest request = CreateScheduleRequest.builder()
    .name("pomo-timer-" + chatId)
    .scheduleExpression("at(" + LocalDateTime.now().plusMinutes(minutes).toString() + ")")
    .target(Target.builder()
        .arn(lambdaArn)
        .roleArn(schedulerRoleArn)
        .input("{\"action\":\"TIMER_DONE\", \"chatId\":\"" + chatId + "\"}")
        .build())
    .actionAfterCompletion(ActionAfterCompletion.DELETE) // Auto-cleanup!
    .build();
```

**Handling the Webhook (Telegram)**

```java
// Inside handleRequest
if (input.containsKey("body")) { 
    // It's a Telegram Webhook
    String body = (String) input.get("body");
    Update update = jsonMapper.readValue(body, Update.class);
    processTelegramUpdate(update);
} else if (input.get("action").equals("TIMER_DONE")) {
    // It's our self-triggered timer
    handleTimerDone(input.get("chatId"));
}
```

**Interactive Buttons (Energy Level)**
When asking the energy question, send a JSON payload with `reply_markup`:

```json
{
  "inline_keyboard": [
    [{"text": "Low 🔋", "callback_data": "ENERGY_LOW"}, {"text": "Medium 🔋🔋", "callback_data": "ENERGY_MED"}],
    [{"text": "High ⚡", "callback_data": "ENERGY_HIGH"}, {"text": "Peak 🚀", "callback_data": "ENERGY_PEAK"}]
  ]
}
```


#### **Step 5: Deployment**

1. **Package**: `mvn clean package`.
2. **Upload**: Create Lambda function, upload JAR. Enable **SnapStart**.
3. **API Gateway**: Create HTTP API $\rightarrow$ Integrate with Lambda.
4. **Webhook**: Run `curl https://api.telegram.org/bot<TOKEN>/setWebhook?url=<API_GATEWAY_URL>`.

***

### **5. Conversation Flow (State Machine)**

| User Action      | Current State        | Bot Logic                                                  | New State            |
| :--------------- | :------------------- | :--------------------------------------------------------- | :------------------- |
| `/start 30`      | `IDLE`               | Creates Schedule (30m). Sends "Focus!".                    | `WORKING`            |
| `/stop`          | `WORKING`            | Deletes Schedule. Sends "Stopped early. What did you do?". | `WAITING_FOR_WORK`   |
| *(Timer fires)*  | `WORKING`            | Sends "Time's up! What did you do?".                       | `WAITING_FOR_WORK`   |
| "Refactored API" | `WAITING_FOR_WORK`   | Saves text to buffer. Sends Energy Buttons.                | `WAITING_FOR_ENERGY` |
| Click "High"     | `WAITING_FOR_ENERGY` | Generates MD. Uploads to Drive. Sends "Saved!".            | `IDLE`               |

### **Security Note**

Since this is personal, add this check at the very top of your `handleRequest`:

```java
long incomingChatId = update.getMessage().getChat().getId();
if (incomingChatId != MY_HARDCODED_ID) {
    return; // Ignore strangers
}
```
