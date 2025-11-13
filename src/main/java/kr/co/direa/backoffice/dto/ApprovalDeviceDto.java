package kr.co.direa.backoffice.dto;

import kr.co.direa.backoffice.domain.ApprovalRequest;
import kr.co.direa.backoffice.domain.ApprovalStep;
import kr.co.direa.backoffice.domain.Departments;
import kr.co.direa.backoffice.domain.DeviceApprovalDetail;
import kr.co.direa.backoffice.domain.DeviceApprovalItem;
import kr.co.direa.backoffice.domain.DeviceTag;
import kr.co.direa.backoffice.domain.Devices;
import kr.co.direa.backoffice.domain.Projects;
import kr.co.direa.backoffice.domain.Tags;
import kr.co.direa.backoffice.domain.enums.ApprovalStatus;
import kr.co.direa.backoffice.domain.enums.DeviceApprovalAction;
import kr.co.direa.backoffice.domain.enums.RealUserMode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
public class ApprovalDeviceDto implements Serializable {
    private String approvalInfo;
    private ApprovalStatus approvalStatus;
    private Long approvalId;
    private String userName;
    private UUID userUuid;
    private String realUser;
    private String realUserMode;
    private String reason;
    private String deviceId;
    private String deviceStatus;
    private String devicePurpose;
    private String description;
    private String categoryName;
    private String img;
    private String type;
    private List<String> deviceIds = new ArrayList<>();
    private List<DeviceItemDto> deviceItems = new ArrayList<>();
    private LocalDateTime submittedAt;
    private LocalDateTime createdDate;
    private LocalDateTime deadline;
    private LocalDateTime usageStartDate;
    private LocalDateTime usageEndDate;
    private String projectName;
    private String projectCode;
    private Long projectId;
    private Long tmpProjectId;
    private String tmpProjectName;
    private String tmpProjectCode;
    private Long tmpDepartmentId;
    private String tmpDepartmentName;
    private List<ApproverDto> approvers = new ArrayList<>();
    private List<String> tags;

    public ApprovalDeviceDto(ApprovalRequest request) {
        this.approvalId = request.getId();
        this.approvalStatus = request.getStatus();
        this.approvalInfo = resolveApprovalInfo(request);
        this.userName = Optional.ofNullable(request.getRequesterName()).orElse(null);
        this.userUuid = request.getRequesterExternalId();
        this.reason = request.getReason();
        this.submittedAt = request.getSubmittedAt();
        this.createdDate = Optional.ofNullable(request.getSubmittedAt()).orElse(request.getCreatedDate());
    this.deadline = request.getDueDate();

        if (request.getSteps() != null) {
            request.getSteps().stream()
                    .sorted(Comparator.comparingInt(ApprovalStep::getSequence))
                    .map(ApproverDto::new)
                    .forEach(approvers::add);
        }

    DeviceApprovalDetail detail = request.getDetail() instanceof DeviceApprovalDetail deviceDetail
    ? deviceDetail
    : null;
    List<Devices> devices = detail != null ? detail.resolveDevices() : List.of();
    List<DeviceApprovalItem> detailItems = detail != null ? detail.getItems() : List.of();
    DeviceApprovalAction action = detail != null ? detail.getAction() : null;
    this.type = Optional.ofNullable(action).map(DeviceApprovalAction::getDisplayName).orElse(null);
    this.img = detail != null ? detail.getAttachmentUrl() : null;
    this.usageStartDate = detail != null ? detail.getRequestedUsageStartDate() : null;
    this.usageEndDate = detail != null ? detail.getRequestedUsageEndDate() : null;
    this.deviceIds = devices.stream()
    .map(Devices::getId)
    .filter(Objects::nonNull)
    .map(String::trim)
    .filter(value -> !value.isBlank())
    .collect(Collectors.collectingAndThen(Collectors.toCollection(LinkedHashSet::new), ArrayList::new));

    String primaryDeviceId = deviceIds.isEmpty() ? null : deviceIds.get(0);
    Devices primaryDevice = resolvePrimaryDevice(devices, primaryDeviceId);

    if (detailItems != null && !detailItems.isEmpty()) {
    detailItems.stream()
        .map(this::buildDeviceItem)
        .forEachOrdered(deviceItems::add);
    }

    DeviceApprovalDetail.RequestedOverrides firstOverride = detail != null && primaryDeviceId != null
        ? detail.requestedOverridesForDevice(primaryDeviceId)
        : null;

    String requestedRealUser = firstNonBlank(
        firstOverride != null ? firstOverride.realUser() : null,
        detail != null ? detail.getRequestedRealUser() : null);

    RealUserMode resolvedMode = Optional.ofNullable(firstOverride)
        .map(DeviceApprovalDetail.RequestedOverrides::realUserMode)
        .orElse(RealUserMode.AUTO);

    this.realUser = firstNonBlank(
        requestedRealUser,
        Optional.ofNullable(primaryDevice).map(Devices::getRealUser).orElse(null));
    this.realUserMode = resolvedMode.getKey();

    this.deviceId = Optional.ofNullable(primaryDevice).map(Devices::getId).orElse(null);
    this.categoryName = Optional.ofNullable(primaryDevice)
        .map(Devices::getCategoryId)
        .map(category -> category.getName())
        .orElse(null);
    this.deviceStatus = Optional.ofNullable(detail)
        .map(DeviceApprovalDetail::getRequestedStatus)
    .orElseGet(() -> Optional.ofNullable(primaryDevice).map(Devices::getStatus).orElse(null));
    this.devicePurpose = Optional.ofNullable(detail)
        .map(DeviceApprovalDetail::getRequestedPurpose)
    .orElseGet(() -> Optional.ofNullable(primaryDevice).map(Devices::getPurpose).orElse(null));
    this.description = Optional.ofNullable(primaryDevice).map(Devices::getDescription).orElse(null);

    this.tags = Optional.ofNullable(primaryDevice)
        .map(Devices::getDeviceTags)
        .orElseGet(Collections::emptySet)
        .stream()
        .map(DeviceTag::getTag)
        .filter(Objects::nonNull)
        .map(Tags::getName)
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(name -> !name.isBlank())
        .collect(Collectors.collectingAndThen(
            Collectors.toCollection(LinkedHashSet::new),
            ArrayList::new));

        Projects currentProject = Optional.ofNullable(primaryDevice).map(Devices::getProjectId).orElse(null);
        this.projectId = Optional.ofNullable(currentProject).map(Projects::getId).orElse(null);
        this.projectName = Optional.ofNullable(currentProject).map(Projects::getName).orElse(null);
    this.projectCode = Optional.ofNullable(currentProject).map(Projects::getCode).orElse(null);

    Projects requestedProject = firstOverride != null && firstOverride.project() != null
        ? firstOverride.project()
        : (detail != null ? detail.getRequestedProject() : null);
    this.tmpProjectId = Optional.ofNullable(requestedProject).map(Projects::getId).orElse(null);
    this.tmpProjectName = firstNonBlank(
        firstOverride != null ? firstOverride.projectName() : null,
        Optional.ofNullable(requestedProject).map(Projects::getName).orElse(null));
    this.tmpProjectCode = firstNonBlank(
        firstOverride != null ? firstOverride.projectCode() : null,
        Optional.ofNullable(requestedProject).map(Projects::getCode).orElse(null));

    Departments requestedDepartment = firstOverride != null && firstOverride.department() != null
        ? firstOverride.department()
        : (detail != null ? detail.getRequestedDepartment() : null);
    this.tmpDepartmentId = Optional.ofNullable(requestedDepartment).map(Departments::getId).orElse(null);
    this.tmpDepartmentName = firstNonBlank(
        firstOverride != null ? firstOverride.departmentName() : null,
        Optional.ofNullable(requestedDepartment).map(Departments::getName).orElse(null));

    if (deviceItems.isEmpty() && primaryDevice != null) {
        deviceItems.add(buildFallbackItem(primaryDevice, requestedProject, requestedDepartment, this.realUser, resolvedMode));
    }
    }

    private Devices resolvePrimaryDevice(List<Devices> devices, String primaryDeviceId) {
        if (devices == null || devices.isEmpty()) {
            return null;
        }
        if (primaryDeviceId != null) {
            for (Devices device : devices) {
                if (device != null && primaryDeviceId.equals(device.getId())) {
                    return device;
                }
            }
        }
        return devices.get(0);
    }

    private DeviceItemDto buildDeviceItem(DeviceApprovalItem item) {
        DeviceItemDto dto = new DeviceItemDto();
        if (item == null) {
            return dto;
        }

        Devices linkedDevice = item.getDevice();
        dto.setDeviceId(linkedDevice != null ? linkedDevice.getId() : null);
        dto.setCategoryName(Optional.ofNullable(linkedDevice)
                .map(Devices::getCategoryId)
                .map(category -> category.getName())
                .orElse(null));
        dto.setStatus(firstNonBlank(
                item.getDeviceSnapshotStatus(),
                Optional.ofNullable(linkedDevice).map(Devices::getStatus).orElse(null)));
        dto.setPurpose(firstNonBlank(
                item.getDeviceSnapshotPurpose(),
                Optional.ofNullable(linkedDevice).map(Devices::getPurpose).orElse(null)));

        Projects requestedProject = item.getRequestedProject();
        dto.setRequestedProjectId(Optional.ofNullable(requestedProject).map(Projects::getId).orElse(null));
        dto.setRequestedProjectName(firstNonBlank(
                item.getRequestedProjectName(),
                Optional.ofNullable(requestedProject).map(Projects::getName).orElse(null)));
        dto.setRequestedProjectCode(firstNonBlank(
                item.getRequestedProjectCode(),
                Optional.ofNullable(requestedProject).map(Projects::getCode).orElse(null)));

        Departments requestedDepartment = item.getRequestedDepartment();
        dto.setRequestedDepartmentId(Optional.ofNullable(requestedDepartment).map(Departments::getId).orElse(null));
        dto.setRequestedDepartmentName(firstNonBlank(
                item.getRequestedDepartmentName(),
                Optional.ofNullable(requestedDepartment).map(Departments::getName).orElse(null)));

        dto.setRequestedRealUser(firstNonBlank(item.getRequestedRealUser(), null));
        dto.setRequestedRealUserMode(Optional.ofNullable(item.getRequestedRealUserMode())
                .orElse(RealUserMode.AUTO)
                .getKey());

        dto.setCurrentProjectName(Optional.ofNullable(linkedDevice)
                .map(Devices::getProjectId)
                .map(Projects::getName)
                .orElse(null));
        dto.setCurrentProjectCode(Optional.ofNullable(linkedDevice)
                .map(Devices::getProjectId)
                .map(Projects::getCode)
                .orElse(null));
        dto.setCurrentDepartmentName(Optional.ofNullable(linkedDevice)
                .map(Devices::getManageDep)
                .map(Departments::getName)
                .orElse(null));
        dto.setCurrentRealUser(Optional.ofNullable(linkedDevice).map(Devices::getRealUser).orElse(null));
        return dto;
    }

    private DeviceItemDto buildFallbackItem(Devices device,
                                            Projects requestedProject,
                                            Departments requestedDepartment,
                                            String requestedRealUserValue,
                                            RealUserMode mode) {
        DeviceItemDto dto = new DeviceItemDto();
        if (device == null) {
            return dto;
        }
        dto.setDeviceId(device.getId());
        dto.setCategoryName(Optional.ofNullable(device.getCategoryId())
                .map(category -> category.getName())
                .orElse(null));
        dto.setStatus(device.getStatus());
        dto.setPurpose(device.getPurpose());
        dto.setRequestedProjectId(Optional.ofNullable(requestedProject).map(Projects::getId).orElse(null));
        dto.setRequestedProjectName(Optional.ofNullable(requestedProject).map(Projects::getName).orElse(null));
        dto.setRequestedProjectCode(Optional.ofNullable(requestedProject).map(Projects::getCode).orElse(null));
        dto.setRequestedDepartmentId(Optional.ofNullable(requestedDepartment).map(Departments::getId).orElse(null));
        dto.setRequestedDepartmentName(Optional.ofNullable(requestedDepartment).map(Departments::getName).orElse(null));
        dto.setRequestedRealUser(firstNonBlank(requestedRealUserValue, null));
        dto.setRequestedRealUserMode(Optional.ofNullable(mode).orElse(RealUserMode.AUTO).getKey());
        dto.setCurrentProjectName(Optional.ofNullable(device.getProjectId()).map(Projects::getName).orElse(null));
        dto.setCurrentProjectCode(Optional.ofNullable(device.getProjectId()).map(Projects::getCode).orElse(null));
        dto.setCurrentDepartmentName(Optional.ofNullable(device.getManageDep()).map(Departments::getName).orElse(null));
        dto.setCurrentRealUser(device.getRealUser());
        return dto;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return null;
    }

    private String resolveApprovalInfo(ApprovalRequest request) {
        ApprovalStatus status = request.getStatus();
        if (status == null) {
            return null;
        }
        if (status == ApprovalStatus.IN_PROGRESS) {
            List<ApprovalStep> steps = request.getSteps();
            long approvedCount = steps == null
                    ? 0
                    : steps.stream()
                            .filter(ApprovalStep::isApproved)
                            .count();
            if (approvedCount > 0) {
                return approvedCount + "차승인완료";
            }
            return ApprovalStatus.PENDING.getDisplayName();
        }
        return status.getDisplayName();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class DeviceItemDto implements Serializable {
        private String deviceId;
        private String categoryName;
        private String status;
        private String purpose;
        private Long requestedProjectId;
        private String requestedProjectName;
        private String requestedProjectCode;
        private Long requestedDepartmentId;
        private String requestedDepartmentName;
        private String requestedRealUser;
        private String requestedRealUserMode;
        private String currentProjectName;
        private String currentProjectCode;
        private String currentDepartmentName;
        private String currentRealUser;
    }
}
