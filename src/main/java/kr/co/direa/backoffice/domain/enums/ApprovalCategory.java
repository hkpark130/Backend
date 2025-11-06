package kr.co.direa.backoffice.domain.enums;

import lombok.Getter;

@Getter
public enum ApprovalCategory {
    DEVICE("장비"),
    LEAVE("휴가"),
    SEMINAR("세미나"),
    EXPENSE("경비");

    private final String displayName;

    ApprovalCategory(String displayName) {
        this.displayName = displayName;
    }
}
