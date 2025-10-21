package kr.co.direa.backoffice.repository;

import kr.co.direa.backoffice.domain.Approver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApproverRepository extends JpaRepository<Approver, Long> {

    Optional<Approver> findByApprovalsIdAndUsersId(Long approvalId, Long userId);

    List<Approver> findByApprovalsId(Long approvalId);

    Optional<Approver> findByApprovalsIdAndStep(Long approvalId, int step);
}
