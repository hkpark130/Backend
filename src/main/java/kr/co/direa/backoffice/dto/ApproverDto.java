package kr.co.direa.backoffice.dto;

import kr.co.direa.backoffice.domain.ApprovalRequest;
import kr.co.direa.backoffice.domain.ApprovalStep;
import kr.co.direa.backoffice.domain.enums.StepStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class ApproverDto {
    private Long approvalId;
    private String username;
    private UUID userUuid;
    private Boolean isApproved;
    private Boolean isRejected;
    private int step;
    private String status;
    private LocalDateTime decidedAt;
    private String comment;

    public ApproverDto(ApprovalStep entity) {
        this.username = entity.getApproverName();
        this.userUuid = entity.getApproverExternalId();
        ApprovalRequest request = entity.getRequest();
        this.approvalId = request != null ? request.getId() : null;
        this.step = entity.getSequence();
        this.isApproved = entity.getStatus() == StepStatus.APPROVED;
        this.isRejected = entity.getStatus() == StepStatus.REJECTED;
        this.status = entity.getStatus().getDisplayName();
        this.decidedAt = entity.getDecidedAt();
        this.comment = entity.getComment();
    }
}
