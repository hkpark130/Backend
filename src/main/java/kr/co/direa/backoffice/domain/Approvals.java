package kr.co.direa.backoffice.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "approvals")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Approvals extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "approval_info")
    private String approvalInfo;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @OneToMany(mappedBy = "approvals", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Approver> approvers = new ArrayList<>();

    @Column(name = "deadline")
    private LocalDateTime deadline;

    protected Approvals() {
    }

    protected Approvals(Long id, String approvalInfo, String reason,
                        List<Approver> approvers, LocalDateTime deadline) {
        this.id = id;
        this.approvalInfo = approvalInfo;
        this.reason = reason;
        this.deadline = deadline;
        this.approvers = approvers;
    }
}
