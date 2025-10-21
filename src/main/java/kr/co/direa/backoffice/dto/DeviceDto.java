package kr.co.direa.backoffice.dto;

import kr.co.direa.backoffice.domain.ApprovalDevices;
import kr.co.direa.backoffice.domain.DeviceTag;
import kr.co.direa.backoffice.domain.Devices;
import kr.co.direa.backoffice.domain.Projects;
import kr.co.direa.backoffice.domain.Users;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Getter
@Setter
@NoArgsConstructor
public class DeviceDto implements Serializable {
    private String id;
    private Long userId;
    private String username;
    private String userEmail;
    private String realUser;
    private String manageDepName;
    private String categoryName;
    private String projectName;
    private String projectCode;
    private String spec;
    private Long price;
    private String model;
    private String description;
    private String adminDescription;
    private List<String> tags;
    private String company;
    private String sn;
    private String status;
    private Boolean isUsable;
    private String purpose;
    private Date purchaseDate;
    private String approvalInfo;
    private String approvalType;
    private Long approvalId;
    private LocalDateTime deadline;
    private List<Map<String, Object>> history;

    public DeviceDto(Devices entity, List<Map<String, Object>> history) {
    this.id = entity.getId();
    Users user = entity.getUserId();
    this.userId = Optional.ofNullable(user).map(Users::getId).orElse(null);
    this.username = Optional.ofNullable(user).map(Users::getUsername).orElse(null);
    this.userEmail = Optional.ofNullable(user).map(Users::getEmail).orElse(null);
        this.realUser = entity.getRealUser();
        this.manageDepName = Optional.ofNullable(entity.getManageDep()).map(dep -> dep.getName()).orElse(null);
        this.categoryName = Optional.ofNullable(entity.getCategoryId()).map(category -> category.getName()).orElse(null);
        Projects project = entity.getProjectId();
        this.projectName = Optional.ofNullable(project).map(Projects::getName).orElse(null);
        this.projectCode = Optional.ofNullable(project).map(Projects::getCode).orElse(null);
        this.spec = entity.getSpec();
        this.price = entity.getPrice();
        this.model = entity.getModel();
        this.description = entity.getDescription();
        this.adminDescription = entity.getAdminDescription();
        this.company = entity.getCompany();
        this.sn = entity.getSn();
        this.status = entity.getStatus();
        this.isUsable = entity.getIsUsable();
        this.purpose = entity.getPurpose();
        this.purchaseDate = entity.getPurchaseDate();
        this.tags = entity.getDeviceTags().stream()
                .map(DeviceTag::getTag)
                .filter(tag -> tag != null)
                .map(tag -> tag.getName())
                .toList();

        Optional<ApprovalDevices> latestApprovalDevice = entity.getApprovalDevices().stream()
                .max(Comparator.comparing(ApprovalDevices::getCreatedDate,
                        Comparator.nullsFirst(Comparator.naturalOrder())));
        latestApprovalDevice.ifPresent(approval -> {
            this.approvalInfo = approval.getApprovalInfo();
            this.approvalType = approval.getType();
            this.deadline = approval.getDeadline();
            this.approvalId = approval.getId();
        });
        this.history = history;
    }
}
