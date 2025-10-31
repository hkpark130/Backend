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
@Table(name = "approver")
public class Approver extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approval_id")
    private Approvals approvals;

    @Column(name = "is_approved")
    private Boolean isApproved;

    @Column(name = "step")
    private int step;

    @Builder
    public Approver(Approvals approvals, Boolean isApproved, int step) {
        this.approvals = approvals;
        this.isApproved = isApproved;
        this.step = step;
    }
}
