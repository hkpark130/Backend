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
			String content,
			String authorName,
			String authorEmail,
			UUID authorExternalId) {
		this.request = request;
		this.content = content;
		this.authorName = authorName;
		this.authorEmail = authorEmail;
		this.authorExternalId = authorExternalId;
	}
}
