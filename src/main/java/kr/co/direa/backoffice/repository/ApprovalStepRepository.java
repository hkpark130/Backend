package kr.co.direa.backoffice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import kr.co.direa.backoffice.domain.ApprovalStep;

public interface ApprovalStepRepository extends JpaRepository<ApprovalStep, Long> {

    Optional<ApprovalStep> findTopByRequestIdAndSequence(Long requestId, int sequence);

    List<ApprovalStep> findByRequestId(Long requestId);

    Optional<ApprovalStep> findByRequestIdAndApproverExternalId(Long requestId, UUID approverExternalId);

    Optional<ApprovalStep> findByRequestIdAndApproverName(Long requestId, String approverName);
}
