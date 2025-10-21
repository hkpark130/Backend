package kr.co.direa.backoffice.domain;

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
	@JoinColumn(name = "approval_id")
	private Approvals approvals;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private Users user;

	@Column(name = "content", columnDefinition = "TEXT")
	private String content;

	@Builder
	public ApprovalComment(Approvals approvals, Users user, String content) {
		this.approvals = approvals;
		this.user = user;
		this.content = content;
	}
}
