package kr.co.direa.backoffice.dto;

import kr.co.direa.backoffice.domain.ApprovalDevices;
import kr.co.direa.backoffice.domain.Approver;
import kr.co.direa.backoffice.domain.Departments;
import kr.co.direa.backoffice.domain.Devices;
import kr.co.direa.backoffice.domain.Projects;
import kr.co.direa.backoffice.domain.Users;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
public class ApprovalDeviceDto implements Serializable {
    private String approvalInfo;
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

    public ApprovalDeviceDto(ApprovalDevices entity) {
        this.approvalInfo = entity.getApprovalInfo();
        this.approvalId = entity.getId();
    Users user = entity.getUserId();
    this.userId = Optional.ofNullable(user).map(Users::getId).orElse(null);
    this.userName = Optional.ofNullable(user).map(Users::getUsername).orElse(null);
        this.realUser = Optional.ofNullable(entity.getDeviceId()).map(Devices::getRealUser).orElse(null);
        this.reason = entity.getReason();
        if (entity.getApprovers() != null) {
            entity.getApprovers().stream()
                    .sorted(Comparator.comparingInt(Approver::getStep))
                    .forEach(approver -> approvers.add(new ApproverDto(approver)));
        }
        this.deviceId = Optional.ofNullable(entity.getDeviceId()).map(Devices::getId).orElse(null);
        this.categoryName = Optional.ofNullable(entity.getDeviceId())
                .map(Devices::getCategoryId)
                .map(category -> category.getName())
                .orElse(null);
        this.deviceStatus = Optional.ofNullable(entity.getDeviceId()).map(Devices::getStatus).orElse(null);
        this.devicePurpose = Optional.ofNullable(entity.getDeviceId()).map(Devices::getPurpose).orElse(null);
        this.description = Optional.ofNullable(entity.getDeviceId()).map(Devices::getDescription).orElse(null);
        this.img = entity.getImg();
        this.type = entity.getType();
        this.createdDate = entity.getCreatedDate();
        this.deadline = entity.getDeadline();
    Projects project = entity.getProjectId();
    this.projectName = Optional.ofNullable(project).map(Projects::getName).orElse(null);
    this.projectId = Optional.ofNullable(project).map(Projects::getId).orElse(null);
    Projects tmpProject = entity.getTmpProject();
    this.tmpProjectId = Optional.ofNullable(tmpProject).map(Projects::getId).orElse(null);
    this.tmpProjectName = Optional.ofNullable(tmpProject).map(Projects::getName).orElse(null);
    Departments tmpDepartment = entity.getTmpDepartment();
    this.tmpDepartmentId = Optional.ofNullable(tmpDepartment).map(Departments::getId).orElse(null);
    this.tmpDepartmentName = Optional.ofNullable(tmpDepartment).map(Departments::getName).orElse(null);
    }

    public ApprovalDevices toEntity() {
        return ApprovalDevices.builder()
                .approvalInfo(approvalInfo)
                .approvers(approvers.stream().map(ApproverDto::toEntity).collect(Collectors.toList()))
        .userId(userId != null ? Users.builder().id(userId).build() : null)
                .deviceId(deviceId != null ? Devices.builder().id(deviceId).build() : null)
                .reason(reason)
                .type(type)
                .img(img)
                .deadline(deadline)
                .projectId(projectId != null ? Projects.builder().id(projectId).build() : null)
                .tmpProject(tmpProjectId != null ? Projects.builder().id(tmpProjectId).build() : null)
                .tmpDepartment(tmpDepartmentId != null ? Departments.builder().id(tmpDepartmentId).build() : null)
                .build();
    }
}
