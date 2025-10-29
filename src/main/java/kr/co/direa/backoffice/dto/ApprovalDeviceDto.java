package kr.co.direa.backoffice.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import kr.co.direa.backoffice.domain.ApprovalRequest;
import kr.co.direa.backoffice.domain.ApprovalStep;
import kr.co.direa.backoffice.domain.Departments;
import kr.co.direa.backoffice.domain.DeviceApprovalDetail;
import kr.co.direa.backoffice.domain.Devices;
import kr.co.direa.backoffice.domain.Projects;
import kr.co.direa.backoffice.domain.Users;
import kr.co.direa.backoffice.domain.enums.ApprovalStatus;
import kr.co.direa.backoffice.domain.enums.DeviceApprovalAction;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ApprovalDeviceDto implements Serializable {
    private String approvalInfo;
    private ApprovalStatus approvalStatus;
    private Long approvalId;
    private Long userId;
    private String userName;
    private String realUser;
    private String reason;
    private String deviceId;
    private String deviceStatus;
    private String devicePurpose;
    private String description;
    private String categoryName;
    private String img;
    private String type;
    private LocalDateTime createdDate;
    private LocalDateTime deadline;
    private String projectName;
    private Long projectId;
    private Long tmpProjectId;
    private String tmpProjectName;
    private Long tmpDepartmentId;
    private String tmpDepartmentName;
    private List<ApproverDto> approvers = new ArrayList<>();

    public ApprovalDeviceDto(ApprovalRequest request) {
    this.approvalId = request.getId();
    this.approvalStatus = request.getStatus();
    this.approvalInfo = Optional.ofNullable(request.getStatus())
        .map(ApprovalStatus::getDisplayName)
        .orElse(null);
    Users requester = request.getRequester();
    this.userId = Optional.ofNullable(requester).map(Users::getId).orElse(null);
    this.userName = Optional.ofNullable(requester).map(Users::getUsername).orElse(request.getRequesterName());
    this.reason = request.getReason();
    this.createdDate = request.getCreatedDate();
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

    Projects currentProject = Optional.ofNullable(device).map(Devices::getProjectId).orElse(null);
    this.projectId = Optional.ofNullable(currentProject).map(Projects::getId).orElse(null);
    this.projectName = Optional.ofNullable(currentProject).map(Projects::getName).orElse(null);

    Projects requestedProject = detail != null ? detail.getRequestedProject() : null;
    this.tmpProjectId = Optional.ofNullable(requestedProject).map(Projects::getId).orElse(null);
    this.tmpProjectName = Optional.ofNullable(requestedProject).map(Projects::getName).orElse(null);

    Departments requestedDepartment = detail != null ? detail.getRequestedDepartment() : null;
    this.tmpDepartmentId = Optional.ofNullable(requestedDepartment).map(Departments::getId).orElse(null);
    this.tmpDepartmentName = Optional.ofNullable(requestedDepartment).map(Departments::getName).orElse(null);
    }
}
