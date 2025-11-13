package kr.co.direa.backoffice.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import kr.co.direa.backoffice.domain.enums.DeviceApprovalAction;
import kr.co.direa.backoffice.domain.enums.RealUserMode;

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

    @OneToMany(mappedBy = "detail", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DeviceApprovalItem> items = new ArrayList<>();

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

    public void replaceDevices(List<Devices> devices) {
        replaceDevices(devices, Collections.emptyMap());
    }

    public void replaceDevices(List<Devices> devices, Map<String, RequestedOverrides> overrides) {
        List<DeviceApprovalItem> previousItems = new ArrayList<>(this.items);
        for (DeviceApprovalItem item : previousItems) {
            Devices previousDevice = item.getDevice();
            if (previousDevice != null && previousDevice.getApprovalDetails() != null) {
                previousDevice.getApprovalDetails().remove(this);
            }
        }

        this.items.clear();
        this.device = null;

        if (devices == null || devices.isEmpty()) {
            return;
        }

        Map<String, RequestedOverrides> safeOverrides = overrides != null ? overrides : Collections.emptyMap();
        Set<String> seen = new LinkedHashSet<>();
        List<DeviceApprovalItem> next = new ArrayList<>();
        for (Devices candidate : devices) {
            if (candidate == null || candidate.getId() == null) {
                continue;
            }
            String deviceId = candidate.getId();
            if (!seen.add(deviceId)) {
                continue;
            }

            DeviceApprovalItem item = DeviceApprovalItem.of(this, candidate);
            RequestedOverrides override = safeOverrides.get(deviceId);
            if (override != null) {
                item.applyOverrides(override);
            }
            next.add(item);

            List<DeviceApprovalDetail> approvalDetails = candidate.getApprovalDetails();
            if (approvalDetails != null && !approvalDetails.contains(this)) {
                approvalDetails.add(this);
            }
        }

        if (next.isEmpty()) {
            return;
        }

        this.items.addAll(next);
        syncAggregateFromFirstItem(safeOverrides);
    }

    public List<Devices> resolveDevices() {
        if (items == null || items.isEmpty()) {
            if (device != null) {
                return List.of(device);
            }
            return Collections.emptyList();
        }
        return items.stream()
                .map(DeviceApprovalItem::getDevice)
                .filter(Objects::nonNull)
                .toList();
    }

    public void applyRequestedOverrides(Map<String, RequestedOverrides> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return;
        }

        Map<String, DeviceApprovalItem> itemMap = buildItemMapByDeviceId();
        overrides.forEach((deviceId, override) -> {
            DeviceApprovalItem item = itemMap.get(deviceId);
            if (item != null) {
                item.applyOverrides(override);
            }
        });
        syncAggregateFromFirstItem(overrides);
    }

    public DeviceApprovalItem findItemByDeviceId(String deviceId) {
        if (deviceId == null || deviceId.isBlank() || items == null || items.isEmpty()) {
            return null;
        }
        String normalized = deviceId.trim();
        for (DeviceApprovalItem item : items) {
            Devices candidate = item.getDevice();
            if (candidate != null && normalized.equals(candidate.getId())) {
                return item;
            }
        }
        return null;
    }

    public Map<String, RequestedOverrides> exportRequestedOverrides() {
        if (items == null || items.isEmpty()) {
            if (device == null || device.getId() == null) {
                return Collections.emptyMap();
            }
            RequestedOverrides fallback = new RequestedOverrides(
                    requestedProject,
                    Optional.ofNullable(requestedProject).map(Projects::getName).orElse(null),
                    Optional.ofNullable(requestedProject).map(Projects::getCode).orElse(null),
                    requestedDepartment,
                    Optional.ofNullable(requestedDepartment).map(Departments::getName).orElse(null),
                    requestedStatus,
                    requestedRealUser,
                    RealUserMode.AUTO);
            return Map.of(device.getId(), fallback);
        }

        Map<String, RequestedOverrides> result = new LinkedHashMap<>();
        for (DeviceApprovalItem item : items) {
            Devices candidate = item.getDevice();
            if (candidate == null || candidate.getId() == null) {
                continue;
            }
            result.put(candidate.getId(), new RequestedOverrides(
                    item.getRequestedProject(),
                    item.getRequestedProjectName(),
                    item.getRequestedProjectCode(),
                    item.getRequestedDepartment(),
                    item.getRequestedDepartmentName(),
                    item.getRequestedStatus(),
                    item.getRequestedRealUser(),
                    Optional.ofNullable(item.getRequestedRealUserMode()).orElse(RealUserMode.AUTO)));
        }
        return result;
    }

    public RequestedOverrides requestedOverridesForDevice(String deviceId) {
        DeviceApprovalItem item = findItemByDeviceId(deviceId);
        if (item == null) {
            return null;
        }
        return new RequestedOverrides(
                item.getRequestedProject(),
                item.getRequestedProjectName(),
                item.getRequestedProjectCode(),
                item.getRequestedDepartment(),
                item.getRequestedDepartmentName(),
                item.getRequestedStatus(),
                item.getRequestedRealUser(),
                Optional.ofNullable(item.getRequestedRealUserMode()).orElse(RealUserMode.AUTO));
    }

    private Map<String, DeviceApprovalItem> buildItemMapByDeviceId() {
        Map<String, DeviceApprovalItem> map = new LinkedHashMap<>();
        if (items == null) {
            return map;
        }
        for (DeviceApprovalItem item : items) {
            Devices candidate = item.getDevice();
            if (candidate == null || candidate.getId() == null) {
                continue;
            }
            map.put(candidate.getId(), item);
        }
        return map;
    }

    private void syncAggregateFromFirstItem(Map<String, RequestedOverrides> overrides) {
        if (items == null || items.isEmpty()) {
            this.device = null;
            return;
        }
        DeviceApprovalItem firstItem = items.get(0);
        Devices primary = firstItem.getDevice();
        this.device = primary;

        RequestedOverrides override = null;
        if (primary != null && overrides != null) {
            override = overrides.get(primary.getId());
        }

        String normalizedStatus = null;

        if (override != null) {
            this.requestedProject = override.project();
            this.requestedDepartment = override.department();
            this.requestedRealUser = normalizeRealUser(override.realUser());
            normalizedStatus = normalizeStatus(override.status());
        } else {
            this.requestedProject = firstItem.getRequestedProject();
            this.requestedDepartment = firstItem.getRequestedDepartment();
            this.requestedRealUser = normalizeRealUser(firstItem.getRequestedRealUser());
            normalizedStatus = normalizeStatus(firstItem.getRequestedStatus());
        }

        this.requestedStatus = normalizedStatus;
    }

    private String normalizeRealUser(String realUser) {
        if (realUser == null) {
            return null;
        }
        String trimmed = realUser.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeStatus(String status) {
        if (status == null) {
            return null;
        }
        String trimmed = status.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record RequestedOverrides(
            Projects project,
            String projectName,
            String projectCode,
            Departments department,
            String departmentName,
            String status,
            String realUser,
            RealUserMode realUserMode) {
    }
}
