package kr.co.direa.backoffice.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Builder;
import kr.co.direa.backoffice.domain.enums.ApprovalCategory;
import kr.co.direa.backoffice.domain.enums.ApprovalStatus;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "approval_requests")
public class ApprovalRequest extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "requester_name", length = 100)
    private String requesterName;

    @Column(name = "requester_email", length = 255)
    private String requesterEmail;

    @Column(name = "requester_external_id", length = 36)
    private UUID requesterExternalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private ApprovalCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ApprovalStatus status;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ApprovalStep> steps = new ArrayList<>();

    @OneToMany(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ApprovalComment> comments = new ArrayList<>();

    @OneToOne(mappedBy = "request", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ApprovalDetail detail;

    @Builder
    public ApprovalRequest(ApprovalCategory category,
                           ApprovalStatus status,
                           String title,
                           String reason,
                           LocalDateTime dueDate) {
        this.category = category;
        this.status = status;
        this.title = title;
        this.reason = reason;
        this.dueDate = dueDate;
    }

    public void addStep(ApprovalStep step) {
        if (step == null) {
            return;
        }
        step.setRequest(this);
        this.steps.add(step);
    }

    public void setDetail(ApprovalDetail detail) {
        this.detail = detail;
        if (detail != null && detail.getRequest() != this) {
            detail.setRequest(this);
        }
    }

    public void markSubmitted() {
        this.submittedAt = LocalDateTime.now();
        this.status = ApprovalStatus.PENDING;
        this.steps.forEach(ApprovalStep::reset);
        if (!this.steps.isEmpty()) {
            this.steps.get(0).begin();
        }
    }

    public void markApproved() {
        this.status = ApprovalStatus.APPROVED;
        this.completedAt = LocalDateTime.now();
    }

    public void markRejected() {
        this.status = ApprovalStatus.REJECTED;
        this.completedAt = LocalDateTime.now();
    }

    public void markCancelled() {
        this.status = ApprovalStatus.CANCELLED;
        this.completedAt = LocalDateTime.now();
    }

    public void restartWorkflow() {
        this.completedAt = null;
        markSubmitted();
    }
}
