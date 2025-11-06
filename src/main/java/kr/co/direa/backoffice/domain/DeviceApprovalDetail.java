package kr.co.direa.backoffice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import kr.co.direa.backoffice.domain.enums.DeviceApprovalAction;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "device_approval_details")
public class DeviceApprovalDetail extends ApprovalDetail {

    public static DeviceApprovalDetail create() {
        return new DeviceApprovalDetail();
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Devices device;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private DeviceApprovalAction action;

    @Column(name = "attachment_url", length = 500)
    private String attachmentUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_project_id")
    private Projects requestedProject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_department_id")
    private Departments requestedDepartment;

    @Column(name = "requested_real_user", length = 100)
    private String requestedRealUser;

    @Column(name = "requested_status", length = 50)
    private String requestedStatus;

    @Column(name = "requested_purpose", length = 255)
    private String requestedPurpose;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    @Column(name = "requested_usage_start_date")
    private LocalDateTime requestedUsageStartDate;

    @Column(name = "requested_usage_end_date")
    private LocalDateTime requestedUsageEndDate;

    public void updateFromDevice(Devices device) {
        if (device == null) {
            return;
        }
        this.device = device;
        this.requestedStatus = device.getStatus();
        this.requestedPurpose = device.getPurpose();
        this.requestedRealUser = device.getRealUser();
    }
}
