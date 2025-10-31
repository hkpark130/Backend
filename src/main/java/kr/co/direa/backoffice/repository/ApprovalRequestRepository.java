package kr.co.direa.backoffice.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import kr.co.direa.backoffice.domain.ApprovalRequest;
import kr.co.direa.backoffice.domain.enums.ApprovalCategory;
import kr.co.direa.backoffice.domain.enums.ApprovalStatus;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    @EntityGraph(attributePaths = {"detail", "steps"})
    List<ApprovalRequest> findByCategoryAndStatusIn(ApprovalCategory category, List<ApprovalStatus> statuses);
}
