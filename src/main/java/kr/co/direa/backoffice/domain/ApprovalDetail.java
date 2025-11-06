package kr.co.direa.backoffice.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Id;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@Entity
@Table(name = "approval_details")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class ApprovalDetail {
    @Id
    private Long id;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id")
    private ApprovalRequest request;

    public void attachTo(ApprovalRequest request) {
        this.request = request;
        request.setDetail(this);
    }
}
