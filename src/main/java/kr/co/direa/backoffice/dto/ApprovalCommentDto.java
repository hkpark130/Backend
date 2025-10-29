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
    private String authorName;
    private String authorEmail;
    private String authorExternalId;
    private String content;
    private LocalDateTime createdDate;

    public ApprovalCommentDto(ApprovalComment entity) {
        this.id = entity.getId();
        this.approvalId = entity.getRequest() != null ? entity.getRequest().getId() : null;
        if (entity.getUser() != null) {
            this.userId = entity.getUser().getId();
            this.username = entity.getUser().getUsername();
        }
        this.authorName = entity.getAuthorName();
        this.authorEmail = entity.getAuthorEmail();
        this.authorExternalId = entity.getAuthorExternalId() != null
                ? entity.getAuthorExternalId().toString()
                : null;
        if (this.username == null) {
            this.username = this.authorName;
        }
        this.content = entity.getContent();
        this.createdDate = entity.getCreatedDate();
    }
}
