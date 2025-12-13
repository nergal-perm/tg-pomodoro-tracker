package ewc.exocortex.pomodoro;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Handles formatting of the session note using a Markdown template.
 */
public final class NoteFormatter {

    private static final String TEMPLATE_PATH = "/templates/session-note.md";
    private static final String TEMPLATE;

    static {
        try (InputStream is = NoteFormatter.class.getResourceAsStream(TEMPLATE_PATH)) {
            if (is == null) {
                throw new IllegalStateException("Template resource not found: " + TEMPLATE_PATH);
            }
            TEMPLATE = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load template: " + TEMPLATE_PATH, e);
        }
    }

    private NoteFormatter() {
        // Utility class
    }

    public static String format(final SessionData session, final Instant stopTime) {
        String note = TEMPLATE;

        note = replace(note, "{{date}}", formatDate(session.startTime()));
        note = replace(note, "{{startTime}}", formatTime(session.startTime()));
        note = replace(note, "{{stopTime}}", formatTime(stopTime));
        note = replace(note, "{{duration}}", String.valueOf(session.duration()));

        note = replace(note, "{{task}}", session.task());
        note = replace(note, "{{role}}", session.role());
        note = replace(note, "{{productType}}", session.productType());
        note = replace(note, "{{usageContext}}", session.usageContext());
        note = replace(note, "{{workContext}}", session.workContext());
        note = replace(note, "{{resources}}", session.resources());
        note = replace(note, "{{constraints}}", session.constraints());

        note = replace(note, "{{energyLevel}}", session.energyLevel());
        note = replace(note, "{{focusLevel}}", session.focusLevel());
        note = replace(note, "{{qualityLevel}}", session.qualityLevel());
        note = replace(note, "{{summary}}", session.summary());
        note = replace(note, "{{nextStep}}", session.nextStep());

        return note;
    }

    private static String replace(String template, String placeholder, String value) {
        return template.replace(placeholder, value != null ? value : "N/A");
    }

    private static String formatDate(Instant instant) {
        if (instant == null)
            return "N/A";
        return LocalDate.ofInstant(instant, ZoneId.of("Asia/Tbilisi")).toString();
    }

    private static String formatTime(Instant instant) {
        if (instant == null)
            return "N/A";
        return instant.atZone(ZoneId.of("Asia/Tbilisi")).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
