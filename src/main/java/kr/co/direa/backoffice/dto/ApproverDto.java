package kr.co.direa.backoffice.dto;

import java.time.LocalDateTime;
import java.util.Optional;

import kr.co.direa.backoffice.domain.ApprovalRequest;
import kr.co.direa.backoffice.domain.ApprovalStep;
import kr.co.direa.backoffice.domain.Users;
import kr.co.direa.backoffice.domain.enums.StepStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ApproverDto {
    private Long userId;
    private Long approvalId;
    private String username;
    private Boolean isApproved;
    private int step;
    private String status;
    private LocalDateTime decidedAt;
    private String comment;

    public ApproverDto(ApprovalStep entity) {
        Users approver = entity.getApprover();
        this.username = Optional.ofNullable(approver)
                .map(Users::getUsername)
                .orElse(entity.getApproverName());
        this.userId = Optional.ofNullable(approver).map(Users::getId).orElse(null);
        ApprovalRequest request = entity.getRequest();
        this.approvalId = request != null ? request.getId() : null;
        this.step = entity.getSequence();
        this.isApproved = entity.getStatus() == StepStatus.APPROVED;
        this.status = entity.getStatus().getDisplayName();
        this.decidedAt = entity.getDecidedAt();
        this.comment = entity.getComment();
    }
}
