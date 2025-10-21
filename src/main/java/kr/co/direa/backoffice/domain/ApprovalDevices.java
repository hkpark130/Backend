package kr.co.direa.backoffice.domain;

import jakarta.annotation.Nullable;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "approval_devices")
public class ApprovalDevices extends Approvals {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    @Nullable
    private Devices deviceId;

    @Column(name = "img")
    private String img;

    @Column(name = "type")
    private String type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    @Nullable
    private Projects projectId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tmp_project")
    @Nullable
    private Projects tmpProject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tmp_department")
    @Nullable
    private Departments tmpDepartment;

    @Builder
    public ApprovalDevices(Long id, Users userId, String approvalInfo, String reason, List<Approver> approvers,
                           Devices deviceId, String img, String type, LocalDateTime deadline,
                           Projects projectId, Projects tmpProject, Departments tmpDepartment) {
        super(id, userId, approvalInfo, reason, approvers, deadline);
        this.deviceId = deviceId;
        this.img = img;
        this.type = type;
        this.projectId = projectId;
        this.tmpProject = tmpProject;
        this.tmpDepartment = tmpDepartment;
    }
}
