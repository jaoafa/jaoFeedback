package com.jaoafa.feedback.lib;

import java.time.Instant;
import java.time.format.DateTimeParseException;

public class TimeUtil {
    private TimeUtil() {
    }

    public static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
