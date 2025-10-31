package kr.co.direa.backoffice.dto;

import kr.co.direa.backoffice.domain.ApprovalRequest;
import kr.co.direa.backoffice.domain.DeviceApprovalDetail;
import kr.co.direa.backoffice.domain.DeviceTag;
import kr.co.direa.backoffice.domain.Devices;
import kr.co.direa.backoffice.domain.Projects;
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
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
public class DeviceDto implements Serializable {
    private String id;
    private String username;
    private String userEmail;
    private UUID userUuid;
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
        this.username = entity.getRealUser();
        this.userEmail = null;
        this.userUuid = entity.getUserUuid();
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

    Optional<DeviceApprovalDetail> latestApprovalDevice = entity.getApprovalDetails().stream()
        .max(Comparator.comparing(
            detail -> Optional.ofNullable(detail.getRequest())
                .map(ApprovalRequest::getCreatedDate)
                .orElse(null),
            Comparator.nullsFirst(Comparator.naturalOrder())));
        latestApprovalDevice.ifPresent(approval -> {
        this.approvalInfo = Optional.ofNullable(approval.getRequest())
            .map(request -> request.getStatus().getDisplayName())
            .orElse(null);
        this.approvalType = Optional.ofNullable(approval.getAction())
            .map(action -> action.getDisplayName())
            .orElse(null);
        this.deadline = Optional.ofNullable(approval.getRequest()).map(ApprovalRequest::getDueDate).orElse(null);
        this.approvalId = Optional.ofNullable(approval.getRequest()).map(ApprovalRequest::getId).orElse(null);
        });
        this.history = history;
    }
}
