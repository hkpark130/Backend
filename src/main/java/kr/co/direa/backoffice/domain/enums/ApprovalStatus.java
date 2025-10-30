package kr.co.direa.backoffice.domain.enums;

import java.util.Arrays;

import lombok.Getter;

@Getter
public enum ApprovalStatus {
    PENDING("승인대기"),
    IN_PROGRESS("1차승인완료"),
    APPROVED("승인완료"),
    REJECTED("반려"),
    CANCELLED("취소");

    private final String displayName;

    ApprovalStatus(String displayName) {
        this.displayName = displayName;
    }

    public boolean isTerminal() {
        return this == APPROVED || this == REJECTED || this == CANCELLED;
    }

    public static ApprovalStatus fromDisplayName(String displayName) {
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
