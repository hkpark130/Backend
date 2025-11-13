package kr.co.direa.backoffice.domain.enums;

import java.util.Locale;

public enum RealUserMode {
    AUTO,
    MANUAL;

    public static RealUserMode fromKey(String value) {
        if (value == null || value.isBlank()) {
            return AUTO;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "manual", "manual-input", "manual_input", "direct", "custom" -> MANUAL;
            default -> AUTO;
        };
    }

    public String getKey() {
        return name().toLowerCase(Locale.ROOT);
    }
}
