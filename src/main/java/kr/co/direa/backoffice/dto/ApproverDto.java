package kr.co.direa.backoffice.dto;

import kr.co.direa.backoffice.domain.ApprovalDevices;
import kr.co.direa.backoffice.domain.Approver;
import kr.co.direa.backoffice.domain.Users;
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

    public ApproverDto(Approver entity) {
        this.username = entity.getUsers().getUsername();
        this.userId = entity.getUsers().getId();
        this.approvalId = entity.getApprovals().getId();
        this.isApproved = entity.getIsApproved();
        this.step = entity.getStep();
    }

    public Approver toEntity() {
        return Approver.builder()
                .users(Users.builder().username(username).build())
                .approvals(ApprovalDevices.builder().id(approvalId).build())
                .isApproved(isApproved)
                .step(step)
                .build();
    }
}
