package kr.co.direa.backoffice.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import kr.co.direa.backoffice.domain.enums.StepStatus;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "approval_steps")
public class ApprovalStep extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private ApprovalRequest request;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id")
    private Users approver;

    @Column(name = "approver_name", length = 100)
    private String approverName;

    @Column(name = "approver_email", length = 255)
    private String approverEmail;

    @Column(name = "approver_external_id", length = 36)
    private UUID approverExternalId;

    @Column(name = "sequence_no")
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private StepStatus status = StepStatus.PENDING;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Builder
    public ApprovalStep(Users approver, int sequence) {
        this.approver = approver;
        this.approverName = approver != null ? approver.getUsername() : null;
        this.approverEmail = approver != null ? approver.getEmail() : null;
        this.approverExternalId = approver != null ? approver.getExternalId() : null;
        this.sequence = sequence;
        this.status = StepStatus.PENDING;
    }

    public void markApproved(String comment) {
        this.status = StepStatus.APPROVED;
        this.decidedAt = LocalDateTime.now();
        this.comment = comment;
    }

    public void markRejected(String comment) {
        this.status = StepStatus.REJECTED;
        this.decidedAt = LocalDateTime.now();
        this.comment = comment;
    }

    public void begin() {
        if (this.status == StepStatus.PENDING) {
            this.status = StepStatus.IN_PROGRESS;
        }
    }

    public void reset() {
        this.status = StepStatus.PENDING;
        this.decidedAt = null;
        this.comment = null;
    }

    public boolean isApproved() {
        return this.status == StepStatus.APPROVED;
    }

    public boolean isRejected() {
        return this.status == StepStatus.REJECTED;
    }
}
