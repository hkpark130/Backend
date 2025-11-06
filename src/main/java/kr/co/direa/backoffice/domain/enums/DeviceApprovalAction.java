package kr.co.direa.backoffice.domain.enums;

import java.util.Arrays;

import lombok.Getter;

@Getter
public enum DeviceApprovalAction {
    RENTAL("대여"),
    RETURN("반납"),
    DISPOSAL("폐기"),
    RECOVERY("복구"),
    PURCHASE("구매"),
    MODIFY("수정");

    private final String displayName;

    DeviceApprovalAction(String displayName) {
        this.displayName = displayName;
    }

    public static DeviceApprovalAction fromDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return null;
        }
        return Arrays.stream(values())
                .filter(action -> action.displayName.equals(displayName))
                .findFirst()
                .orElseGet(() -> {
                    String normalized = displayName.trim().toUpperCase();
                    return Arrays.stream(values())
                            .filter(action -> action.name().equalsIgnoreCase(normalized))
                            .findFirst()
                            .orElse(null);
                });
    }
}
