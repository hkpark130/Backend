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
    private String authorName;
    private String username;
    private String authorEmail;
    private String authorExternalId;
    private String content;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;

    public ApprovalCommentDto(ApprovalComment entity) {
        this.id = entity.getId();
        this.approvalId = entity.getRequest() != null ? entity.getRequest().getId() : null;
        this.authorName = entity.getAuthorName();
    this.username = entity.getAuthorName();
        this.authorEmail = entity.getAuthorEmail();
        this.authorExternalId = entity.getAuthorExternalId() != null
                ? entity.getAuthorExternalId().toString()
                : null;
        this.content = entity.getContent();
        this.createdDate = entity.getCreatedDate();
        this.modifiedDate = entity.getModifiedDate();
    }
}
