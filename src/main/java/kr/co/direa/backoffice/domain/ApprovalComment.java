package kr.co.direa.backoffice.domain;

import java.util.UUID;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "approval_comment")
public class ApprovalComment extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "request_id")
	private ApprovalRequest request;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private Users user;

	@Column(name = "author_name", length = 100)
	private String authorName;

	@Column(name = "author_email", length = 255)
	private String authorEmail;

	@Column(name = "author_external_id", length = 36)
	private UUID authorExternalId;

	@Column(name = "content", columnDefinition = "TEXT")
	private String content;

	@Builder
	public ApprovalComment(ApprovalRequest request,
			Users user,
			String content,
			String authorName,
			String authorEmail,
			UUID authorExternalId) {
		this.request = request;
		this.user = user;
		this.content = content;
		this.authorName = authorName != null ? authorName : user != null ? user.getUsername() : null;
		this.authorEmail = authorEmail != null ? authorEmail : user != null ? user.getEmail() : null;
		this.authorExternalId = authorExternalId != null ? authorExternalId : user != null ? user.getExternalId() : null;
	}
}
