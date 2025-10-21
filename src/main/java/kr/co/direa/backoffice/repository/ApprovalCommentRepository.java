package kr.co.direa.backoffice.repository;

import kr.co.direa.backoffice.domain.ApprovalComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalCommentRepository extends JpaRepository<ApprovalComment, Long> {

	List<ApprovalComment> findByApprovalsIdOrderByCreatedDateAsc(Long approvalId);
}
