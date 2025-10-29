package kr.co.direa.backoffice.domain.enums;

import java.util.Arrays;

import lombok.Getter;

@Getter
public enum StepStatus {
    PENDING("대기"),
    IN_PROGRESS("진행중"),
    APPROVED("승인"),
    REJECTED("반려"),
    SKIPPED("건너뜀");

    private final String displayName;

    StepStatus(String displayName) {
        this.displayName = displayName;
    }

    public boolean isDecisionMade() {
        return this == APPROVED || this == REJECTED || this == SKIPPED;
    }

    public static StepStatus fromDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.displayName.equals(displayName))
                .findFirst()
                .orElseGet(() -> {
                    String normalized = displayName.trim().toUpperCase();
                    return Arrays.stream(values())
                            .filter(status -> status.name().equalsIgnoreCase(normalized))
                            .findFirst()
                            .orElse(null);
                });
    }
}
