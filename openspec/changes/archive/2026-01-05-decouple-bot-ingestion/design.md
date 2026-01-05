# Design: Decoupled Ingestion Pipeline

## Context
Currently, the Bot acts as a "Smart Client," formatting markdown and talking directly to the file system (via Drive API). This violates the "Dumb Capture, Smart Kernel" principle. We want to move the "Smart" logic to the MCP Server.

## Decisions

### 1. DynamoDB as Ingestion Buffer
**Decision**: Use AWS DynamoDB to store completed sessions as raw JSON records.
**Why**:
- Serverless-native (fits Lambda architecture).
- Decouples capture availability from Vault availability.
- Allows the MCP Server to poll/process at its own pace.

## Data Architecture
We will use two distinct DynamoDB tables to separate "Hot State" from "Cold Logs".

### Table 1: `PomodoroBotState` (Existing)
*   **Purpose**: Management of the active user session FSM.
*   **Partition Key**: `chatId` (Number).
*   **Lifecycle**: Row created on `/start`, updated during interaction, **deleted** upon completion.
*   **Consistency**: Strong.

### Table 2: `PomodoroIngestion` (New)
*   **Purpose**: A transient queue for completed session payloads.
*   **Partition Key**: `id` (String, UUID).
*   **Attributes**:
    - `payload` (Map: The full SessionData JSON)
*   **Access Pattern**:
    - **Bot**: `PutItem`.
    - **MCP**: `Scan` (get all items) -> Process -> `DeleteItem` (remove from queue).

### Reassessment of Table Strategy
**Decision**: Maintain a separate `PomodoroIngestion` table.
**Rationale**:
1.  **Primary Key Conflict**: The existing `PomodoroBotState` uses `chatId` (Number) as the Primary Key. The Ingestion Queue needs to support multiple pending records (potentially) enabling a UUID PK. Mixing these entry types would require a table migration to a Generic PK (String) or complex Sort Key logic.
2.  **Access Isolation**: The Bot (Interactive) needs low-latency read/write to the State table. The MCP (Batch) will perform expensive Scans on the Ingestion table. Separating them ensures the MCP never impacts the Bot's responsiveness.
3.  **Simplicity**: "Delete-on-consume" is easiest to implement when the table is just a bucket of items without mixing in active state records.


### 2. Schema Reductio Ad Absurdum
**Decision**: Remove `Context`, `Energy`, `Focus`, and `Quality` fields.
**Why**:
- "Friction Reduction" is the primary driver for compliance.
- Subjective metrics proved to be low-signal / high-noise in practice.
- Focus on "What happened" (Outcome) vs "How it felt" (Energy).

## Risks / Trade-offs
- **Latency**: Artifacts won't appear in the Vault immediately; they depend on the MCP Server polling interval.
- **Data Loss**: We lose the ability to track "Energy vs Output" correlation. This is an explicit sacrifice.
- **Complexity**: Introduces a new architectural hop (Bot -> Dynamo -> MCP -> Vault) vs (Bot -> Drive -> Vault).

## Migration
- Old Google Drive notes will remain as is.
- New notes will flow through the new pipeline.
- No backfill required for this change.
