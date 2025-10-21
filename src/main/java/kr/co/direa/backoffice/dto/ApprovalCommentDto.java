package kr.co.direa.backoffice.dto;

import kr.co.direa.backoffice.domain.ApprovalComment;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
public class ApprovalCommentDto implements Serializable {
    private Long id;
    private Long approvalId;
    private Long userId;
    private String username;
    private String content;
    private LocalDateTime createdDate;

    public ApprovalCommentDto(ApprovalComment entity) {
        this.id = entity.getId();
        this.approvalId = entity.getApprovals().getId();
        this.userId = entity.getUser().getId();
        this.username = entity.getUser().getUsername();
        this.content = entity.getContent();
        this.createdDate = entity.getCreatedDate();
    }
}
