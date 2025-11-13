package kr.co.direa.backoffice.domain;

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
import java.util.Optional;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import kr.co.direa.backoffice.domain.Departments;
import kr.co.direa.backoffice.domain.enums.RealUserMode;
import kr.co.direa.backoffice.domain.Projects;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "device_approval_items")
public class DeviceApprovalItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "detail_id", nullable = false)
    private DeviceApprovalDetail detail;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Devices device;

    @Column(name = "device_snapshot_status", length = 50)
    private String deviceSnapshotStatus;

    @Column(name = "device_snapshot_purpose", length = 255)
    private String deviceSnapshotPurpose;

    @Column(name = "requested_status", length = 50)
    private String requestedStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_project_id")
    private Projects requestedProject;

    @Column(name = "requested_project_name", length = 200)
    private String requestedProjectName;

    @Column(name = "requested_project_code", length = 100)
    private String requestedProjectCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_department_id")
    private Departments requestedDepartment;

    @Column(name = "requested_department_name", length = 200)
    private String requestedDepartmentName;

    @Column(name = "requested_real_user", length = 100)
    private String requestedRealUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_real_user_mode", length = 10)
    private RealUserMode requestedRealUserMode;

    public static DeviceApprovalItem of(DeviceApprovalDetail detail, Devices device) {
        DeviceApprovalItem item = new DeviceApprovalItem();
        item.setDetail(detail);
        item.setDevice(device);
        item.setDeviceSnapshotStatus(device != null ? device.getStatus() : null);
        item.setDeviceSnapshotPurpose(device != null ? device.getPurpose() : null);
        item.setRequestedStatus(device != null ? device.getStatus() : null);
        return item;
    }

    public void applyOverrides(DeviceApprovalDetail.RequestedOverrides overrides) {
    if (overrides == null) {
        return;
    }

    String status = Optional.ofNullable(overrides.status())
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .orElse(null);
    setRequestedStatus(status);

    setRequestedProject(overrides.project());
    String projectName = Optional.ofNullable(overrides.projectName())
        .orElseGet(() -> Optional.ofNullable(overrides.project())
            .map(Projects::getName)
            .orElse(null));
    setRequestedProjectName(projectName);

    String projectCode = Optional.ofNullable(overrides.projectCode())
        .orElseGet(() -> Optional.ofNullable(overrides.project())
            .map(Projects::getCode)
            .orElse(null));
    setRequestedProjectCode(projectCode);

    setRequestedDepartment(overrides.department());
    String departmentName = Optional.ofNullable(overrides.departmentName())
        .orElseGet(() -> Optional.ofNullable(overrides.department())
            .map(Departments::getName)
            .orElse(null));
    setRequestedDepartmentName(departmentName);

    String realUser = Optional.ofNullable(overrides.realUser())
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .orElse(null);
    setRequestedRealUser(realUser);
    setRequestedRealUserMode(Optional.ofNullable(overrides.realUserMode())
        .orElse(RealUserMode.AUTO));
    }
}
