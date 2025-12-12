# Deployment Guide: Pomodoro Bot Walking Skeleton

This guide details the steps to configure, deploy, and activate your Serverless Pomodoro Bot using OAuth2 (User) Authentication.

## Prerequisites
- **AWS CLI** installed and configured (`aws configure`)
- **AWS SAM CLI** installed
- **Java 21 (Amazon Corretto)** installed
- **Maven** installed

---

## Step 1: Telegram Setup

1.  **Create Bot**:
    *   Open Telegram and search for **@BotFather**.
    *   Send `/newbot`.
    *   Follow instructions to name your bot.
    *   **Copy the Access Token** (e.g., `123456:ABC-DEF1234ghIkl-zyx57W2v1u123ew11`).

2.  **Get Your Chat ID**:
    *   Search for **@userinfobot** in Telegram.
    *   Start the chat.
    *   **Copy your ID** (integer, e.g., `123456789`).

---

## Step 2: Google Cloud Setup (OAuth2)

1.  **Create Project**: Go to [Google Cloud Console](https://console.cloud.google.com/) and create a new project (e.g., `pomodoro-bot`).
2.  **Enable API**:
    *   Go to **APIs & Services** > **Library**.
    *   Search for **Google Drive API** and enable it.
3.  **Configure OAuth Consent Screen**:
    *   Go to **APIs & Services** > **OAuth consent screen**.
    *   Select **External** -> Create.
    *   App Name: `Pomodoro Bot` (Email: your email).
    *   Add your email to **Test Users**.
    *   Save and Continue.
4.  **Create Credentials**:
    *   Go to **Credentials** > **Create Credentials** > **OAuth client ID**.
    *   Type: **Desktop app**.
    *   Name: `CLI Uploader`.
    *   Copy **Client ID** and **Client Secret**.
5.  **Get Refresh Token**:
    *   Run this `gcloud` command locally (requires Cloud SDK) or use OAuth 2.0 Playground. 
    *   **Easiest way** (via OAuth Playground):
        1. Go to [OAuth 2.0 Playground](https://developers.google.com/oauthplayground/).
        2. Click settings (gear icon) -> Check "Use your own OAuth credentials" -> Paste Client ID/Secret.
        3. Determine scopes: `https://www.googleapis.com/auth/drive.file`.
        4. Click "Authorize APIs", login with your Google account, and grant access.
        5. Click "Exchange authorization code for tokens".
        6. **Copy `Refresh Token`**.
6.  **Create Folder**:
    *   Create a folder in your Drive (e.g., `Pomodoro Logs`).
    *   **Copy the Folder ID**: Last part of URL.

---

## Step 3: Deployment

1.  **Build the Project**:
    ```bash
    mvn clean package
    sam build
    ```

2.  **Deploy to AWS**:
    ```bash
    sam deploy --guided
    ```

3.  **Fill Parameters**:
    *   **Stack Name**: `pomodoro-bot` (or your preference)
    *   **AWS Region**: `us-east-1` (or your preference)
    *   **AdminChatId**: Your Telegram numeric ID.
    *   **TelegramBotToken**: Your Bot Token.
    *   **GoogleClientId**: Paste your Client ID.
    *   **GoogleClientSecret**: Paste your Client Secret.
    *   **GoogleRefreshToken**: Paste the Refresh Token.
    *   **GoogleDriveFolderId**: The folder ID string.
    *   **Confirm changes before deploy**: `y`
    *   **Allow SAM CLI IAM role creation**: `y`
    *   **Save arguments to configuration file**: `y`

---

## Step 4: Activation

After deployment succeeds, SAM will output a **Outputs** section containing the `WebhookUrl`.

1.  **Copy the Webhook URL**:
    It will look like: `https://<api-id>.execute-api.<region>.amazonaws.com/webhook`

2.  **Register Webhook**:
    Run this command in your terminal (replacing placeholders):
    ```bash
    curl "https://api.telegram.org/bot<YOUR_BOT_TOKEN>/setWebhook?url=<YOUR_WEBHOOK_URL>"
    ```

3.  **Test**:
    *   Open your bot in Telegram.
    *   Send `/start` to begin a new work session.
    *   Select a duration and provide a title.
    *   Send `/stop` to end the session and provide a result.
    *   Check your Google Drive folder for the new file.

---

## AWS Infrastructure (Automatic via SAM)

The following resources are **automatically created** by `sam deploy`:

### DynamoDB Table: `PomodoroBotState`
- **Purpose**: Stores session state across Lambda invocations
- **Partition Key**: `chatId` (Number)
- **Billing**: Pay-per-request (no provisioned capacity)
- **Attributes stored**: `status`, `sessionTitle`, `duration`, `startTime`, `scheduleName`

### Lambda Permissions
The Lambda function is automatically granted:
- `dynamodb:PutItem`, `GetItem`, `DeleteItem` on the `PomodoroBotState` table

---

## EventBridge Scheduler (Automatic via SAM)

The timer functionality uses **AWS EventBridge Scheduler** to trigger the Lambda after the work session duration expires.

### Resources Created
- **SchedulerExecutionRole**: IAM role allowing EventBridge to invoke the Lambda
- **Lambda Policies**: Permissions for `scheduler:CreateSchedule`, `scheduler:DeleteSchedule`, and `iam:PassRole`

### Environment Variables
The Lambda receives these for timer management:
- `LAMBDA_ARN`: Target ARN for EventBridge schedules
- `SCHEDULER_ROLE_ARN`: Role ARN for EventBridge to assume

### How It Works
1. User provides session title → Lambda calls `scheduler:CreateSchedule` with `at()` expression
2. Timer fires → EventBridge invokes Lambda with `{"action":"TIMER_DONE","chatId":123}`
3. Lambda prompts user for result
4. If user sends `/stop` → Lambda calls `scheduler:DeleteSchedule` to cancel timer
