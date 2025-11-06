package kr.co.direa.backoffice.dto;

import kr.co.direa.backoffice.domain.ApprovalRequest;
import kr.co.direa.backoffice.domain.ApprovalStep;
import kr.co.direa.backoffice.domain.Departments;
import kr.co.direa.backoffice.domain.DeviceApprovalDetail;
import kr.co.direa.backoffice.domain.DeviceTag;
import kr.co.direa.backoffice.domain.Devices;
import kr.co.direa.backoffice.domain.Projects;
import kr.co.direa.backoffice.domain.Tags;
import kr.co.direa.backoffice.domain.enums.ApprovalStatus;
import kr.co.direa.backoffice.domain.enums.DeviceApprovalAction;
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
    private String reason;
    private String deviceId;
    private String deviceStatus;
    private String devicePurpose;
    private String description;
    private String categoryName;
    private String img;
    private String type;
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
        DeviceApprovalAction action = detail != null ? detail.getAction() : null;
        this.type = Optional.ofNullable(action).map(DeviceApprovalAction::getDisplayName).orElse(null);
        this.img = detail != null ? detail.getAttachmentUrl() : null;
    this.usageStartDate = detail != null ? detail.getRequestedUsageStartDate() : null;
    this.usageEndDate = detail != null ? detail.getRequestedUsageEndDate() : null;
        Devices device = detail != null ? detail.getDevice() : null;
        this.realUser = Optional.ofNullable(detail)
                .map(DeviceApprovalDetail::getRequestedRealUser)
                .orElseGet(() -> Optional.ofNullable(device).map(Devices::getRealUser).orElse(null));
        this.deviceId = Optional.ofNullable(device).map(Devices::getId).orElse(null);
        this.categoryName = Optional.ofNullable(device)
                .map(Devices::getCategoryId)
                .map(category -> category.getName())
                .orElse(null);
        this.deviceStatus = Optional.ofNullable(detail)
                .map(DeviceApprovalDetail::getRequestedStatus)
                .orElseGet(() -> Optional.ofNullable(device).map(Devices::getStatus).orElse(null));
        this.devicePurpose = Optional.ofNullable(detail)
                .map(DeviceApprovalDetail::getRequestedPurpose)
                .orElseGet(() -> Optional.ofNullable(device).map(Devices::getPurpose).orElse(null));
        this.description = Optional.ofNullable(device).map(Devices::getDescription).orElse(null);

    this.tags = Optional.ofNullable(device)
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

        Projects currentProject = Optional.ofNullable(device).map(Devices::getProjectId).orElse(null);
        this.projectId = Optional.ofNullable(currentProject).map(Projects::getId).orElse(null);
        this.projectName = Optional.ofNullable(currentProject).map(Projects::getName).orElse(null);
    this.projectCode = Optional.ofNullable(currentProject).map(Projects::getCode).orElse(null);

        Projects requestedProject = detail != null ? detail.getRequestedProject() : null;
        this.tmpProjectId = Optional.ofNullable(requestedProject).map(Projects::getId).orElse(null);
        this.tmpProjectName = Optional.ofNullable(requestedProject).map(Projects::getName).orElse(null);
    this.tmpProjectCode = Optional.ofNullable(requestedProject).map(Projects::getCode).orElse(null);

        Departments requestedDepartment = detail != null ? detail.getRequestedDepartment() : null;
        this.tmpDepartmentId = Optional.ofNullable(requestedDepartment).map(Departments::getId).orElse(null);
        this.tmpDepartmentName = Optional.ofNullable(requestedDepartment).map(Departments::getName).orElse(null);
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
}
