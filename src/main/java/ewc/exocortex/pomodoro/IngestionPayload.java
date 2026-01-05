package ewc.exocortex.pomodoro;

import java.time.Instant;

/**
 * Clean payload for session ingestion, decoupled from internal session state.
 */
public record IngestionPayload(
        String task,
        String role,
        String productType,
        Instant startTime,
        Instant endTime,
        int duration, // Intended duration in minutes
        String outcome) {
}
