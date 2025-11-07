package kr.co.direa.backoffice.service;

import java.text.Collator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.direa.backoffice.config.ApprovalProperties;
import kr.co.direa.backoffice.constant.Constants;
import kr.co.direa.backoffice.domain.ApprovalRequest;
import kr.co.direa.backoffice.domain.ApprovalStep;
import kr.co.direa.backoffice.domain.Departments;
import kr.co.direa.backoffice.domain.DeviceApprovalDetail;
import kr.co.direa.backoffice.domain.Devices;
import kr.co.direa.backoffice.domain.Projects;
import kr.co.direa.backoffice.domain.enums.ApprovalCategory;
import kr.co.direa.backoffice.domain.enums.ApprovalStatus;
import kr.co.direa.backoffice.domain.enums.DeviceApprovalAction;
import kr.co.direa.backoffice.domain.enums.StepStatus;
import kr.co.direa.backoffice.dto.ApprovalDeviceDto;
import kr.co.direa.backoffice.dto.DeviceApplicationRequestDto;
import kr.co.direa.backoffice.dto.PageResponse;
import kr.co.direa.backoffice.exception.CustomException;
import kr.co.direa.backoffice.exception.code.CustomErrorCode;
import kr.co.direa.backoffice.repository.ApprovalRequestRepository;
import kr.co.direa.backoffice.repository.ApprovalStepRepository;
import kr.co.direa.backoffice.repository.DeviceApprovalDetailRepository;
import kr.co.direa.backoffice.repository.DepartmentsRepository;
import kr.co.direa.backoffice.repository.DevicesRepository;
import kr.co.direa.backoffice.repository.ProjectsRepository;
import kr.co.direa.backoffice.vo.ApprovalSearchRequest;
import kr.co.direa.backoffice.vo.ApprovalUpdateRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ApprovalDeviceService {
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final DevicesRepository devicesRepository;
    private final ProjectsRepository projectsRepository;
    private final DepartmentsRepository departmentsRepository;
    private final NotificationService notificationService;
    private final ApprovalCommentService approvalCommentService;
    private final CommonLookupService commonLookupService;
    private final ApprovalProperties approvalProperties;
    private final DeviceApprovalDetailRepository deviceApprovalDetailRepository;
    private final TagsService tagsService;
    private final MailService mailService;

    @Transactional
    public ApprovalDeviceDto submitApplication(DeviceApplicationRequestDto request) {
    DeviceApprovalAction action = Optional.ofNullable(DeviceApprovalAction.fromDisplayName(request.getType()))
        .orElseThrow(() -> new CustomException(CustomErrorCode.APPROVAL_TYPE_UNSUPPORTED,
            "Unsupported approval type: " + request.getType()));

        Devices device = devicesRepository.findById(request.getDeviceId())
        .orElseThrow(() -> new CustomException(CustomErrorCode.DEVICE_NOT_FOUND,
            "Device not found: " + request.getDeviceId()));
        CommonLookupService.KeycloakUserInfo requesterInfo = resolveUserInfo(request.getUserName());

        validateDuplicateDeviceAction(device, action);

        if (action == DeviceApprovalAction.RETURN) {
            tagsService.replaceDeviceTags(device, request.getTag());
        }
        List<String> approverUsernames = prepareApproverSequence(request.getApprovers(), true);
        if (approverUsernames.isEmpty()) {
        throw new CustomException(CustomErrorCode.APPROVAL_NO_AVAILABLE_APPROVER);
        }

        if ((request.getUsageStartDate() == null) != (request.getUsageEndDate() == null)) {
        throw new CustomException(CustomErrorCode.APPROVAL_USAGE_PERIOD_INCOMPLETE);
        }
        if (request.getUsageStartDate() != null && request.getUsageEndDate() != null
                && request.getUsageEndDate().isBefore(request.getUsageStartDate())) {
        throw new CustomException(CustomErrorCode.APPROVAL_USAGE_END_BEFORE_START);
        }

        String projectIdentifier = firstNonBlank(request.getProjectName(), request.getProjectCode());
        Projects requestedProject = lookupProject(projectIdentifier);
        if (projectIdentifier != null && requestedProject == null) {
            throw new CustomException(CustomErrorCode.PROJECT_NOT_FOUND);
        }
        String departmentName = Optional.ofNullable(request.getDepartmentName())
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse(null);
        Departments requestedDepartment = lookupDepartment(departmentName);
        if (departmentName != null && requestedDepartment == null) {
            throw new CustomException(CustomErrorCode.REQUESTED_DEPARTMENT_NOT_FOUND);
        }

        applyDeviceStateOnSubmission(device, request, action);

        DeviceApprovalDetail detail = DeviceApprovalDetail.create();
        detail.setDevice(device);
        detail.setAction(action);
        detail.setAttachmentUrl(request.getImg());
        detail.setRequestedProject(requestedProject);
        detail.setRequestedDepartment(requestedDepartment);
        detail.setRequestedRealUser(request.getRealUser());
        detail.setRequestedStatus(request.getDeviceStatus());
        detail.setRequestedPurpose(request.getDevicePurpose());
        detail.setRequestedUsageStartDate(request.getUsageStartDate());
        detail.setRequestedUsageEndDate(request.getUsageEndDate());
        detail.setMemo(request.getDescription());
        detail.updateFromDevice(device);

        ApprovalRequest approval = ApprovalRequest.builder()
                .category(ApprovalCategory.DEVICE)
                .status(ApprovalStatus.PENDING)
                .title(buildTitle(action, device))
                .reason(request.getReason())
                .dueDate(request.getDeadline())
                .build();

        applyRequesterMetadata(approval, requesterInfo, request.getUserName());

        detail.attachTo(approval);
        buildApprovalSteps(approval, approverUsernames);
        approval.markSubmitted();

        ApprovalRequest saved = approvalRequestRepository.save(approval);
        saved.getDetail(); // force persistence

        saved.getSteps().stream()
                .filter(step -> step.getStatus() == StepStatus.IN_PROGRESS)
                .findFirst()
                .ifPresent(step -> notifyApprover(saved, step, "새 장비 결재 요청이 도착했습니다."));

        return new ApprovalDeviceDto(saved);
    }

    @Transactional
    public ApprovalDeviceDto approve(Long approvalId, String approverUsername, String comment) {
        ApprovalRequest approval = approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.APPROVAL_NOT_FOUND,
                        "Approval not found: " + approvalId));
        ApprovalStatus currentStatus = approval.getStatus();
        if (currentStatus != null && currentStatus.isTerminal()) {
            throw new CustomException(CustomErrorCode.APPROVAL_ALREADY_TERMINATED);
        }
        CommonLookupService.KeycloakUserInfo approverInfo = resolveUserInfo(approverUsername);
        String normalizedApproverUsername = safeUsername(approverInfo, approverUsername);
        if (normalizedApproverUsername == null) {
            throw new CustomException(CustomErrorCode.APPROVER_USERNAME_REQUIRED);
        }
        UUID approverExternalId = approverInfo != null ? approverInfo.id() : null;

        ApprovalStep step = resolveApprovalStep(approvalId, normalizedApproverUsername, approverExternalId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.APPROVAL_STEP_NOT_ASSIGNED));

        applyApproverMetadata(step, approverInfo, normalizedApproverUsername);

        if (step.getStatus().isDecisionMade()) {
            throw new CustomException(CustomErrorCode.APPROVAL_STEP_ALREADY_DECIDED);
        }

        ensurePreviousStepsApproved(approval, step.getSequence());

        step.markApproved(comment);

        int currentSequence = step.getSequence();
        ApprovalStep nextStep = findNextStep(approval, currentSequence);
        if (nextStep != null) {
            nextStep.begin();
            approval.setStatus(ApprovalStatus.IN_PROGRESS);
            notifyApplicantOnProgress(approval, step);
            int nextSequence = nextStep.getSequence();
            String message = (currentSequence > 0 ? currentSequence + "차 결재가 완료되어 " : "결재가 완료되어 ")
                    + (nextSequence > 0 ? nextSequence + "차 결재가 필요합니다." : "다음 결재가 필요합니다.");
            notifyApprover(approval, nextStep, message);
        } else {
            approval.markApproved();
            applyDeviceStateOnCompletion(approval);
            notifyApplicantOnCompletion(approval);
        }

        if (comment != null && !comment.isBlank()) {
            approvalCommentService.addComment(approvalId, normalizedApproverUsername, comment);
        }

        return new ApprovalDeviceDto(approvalRequestRepository.save(approval));
    }

    @Transactional
    public ApprovalDeviceDto reject(Long approvalId, String approverUsername, String reason) {
        ApprovalRequest approval = approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.APPROVAL_NOT_FOUND,
                        "Approval not found: " + approvalId));
        ApprovalStatus currentStatus = approval.getStatus();
        if (currentStatus != null && currentStatus.isTerminal()) {
            throw new CustomException(CustomErrorCode.APPROVAL_ALREADY_TERMINATED);
        }
        CommonLookupService.KeycloakUserInfo approverInfo = resolveUserInfo(approverUsername);
        String normalizedApproverUsername = safeUsername(approverInfo, approverUsername);
        if (normalizedApproverUsername == null) {
            throw new CustomException(CustomErrorCode.APPROVER_USERNAME_REQUIRED);
        }
        UUID approverExternalId = approverInfo != null ? approverInfo.id() : null;

        ApprovalStep step = resolveApprovalStep(approvalId, normalizedApproverUsername, approverExternalId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.APPROVAL_STEP_NOT_ASSIGNED));

        applyApproverMetadata(step, approverInfo, normalizedApproverUsername);

        StepStatus stepStatus = step.getStatus();
        if (stepStatus == StepStatus.REJECTED) {
            throw new CustomException(CustomErrorCode.APPROVAL_STEP_ALREADY_DECIDED);
        }
        if (stepStatus == StepStatus.APPROVED) {
            rollbackSubsequentSteps(approval, step.getSequence());
        } else if (stepStatus != null && stepStatus.isDecisionMade()) {
            throw new CustomException(CustomErrorCode.APPROVAL_STEP_ALREADY_DECIDED);
        }

        step.markRejected(reason);
        approval.markRejected();

        if (reason != null && !reason.isBlank()) {
            approvalCommentService.addComment(approvalId, normalizedApproverUsername, reason);
        }

    notifyApplicantOnRejection(approval, approverInfo, normalizedApproverUsername, reason);

        return new ApprovalDeviceDto(approvalRequestRepository.save(approval));
    }

    @Transactional
    public ApprovalDeviceDto updateApprovers(Long approvalId, List<String> approverUsernames) {
        ApprovalRequest approval = approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.APPROVAL_NOT_FOUND,
                        "Approval not found: " + approvalId));

        List<String> sanitizedApprovers = prepareApproverSequence(approverUsernames, false);
        if (sanitizedApprovers.isEmpty()) {
            throw new CustomException(CustomErrorCode.APPROVER_LIST_REQUIRED);
        }

        approval.getSteps().clear();
        buildApprovalSteps(approval, sanitizedApprovers);
        approval.restartWorkflow();

        ApprovalRequest saved = approvalRequestRepository.save(approval);
        saved.getSteps().stream()
                .filter(step -> step.getStatus() == StepStatus.IN_PROGRESS)
                .findFirst()
                .ifPresent(step -> notifyApprover(saved, step, "장비 결재 요청 승인자가 변경되었습니다."));

        return new ApprovalDeviceDto(saved);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApprovalDeviceDto> findPendingApprovals(ApprovalSearchRequest request) {
        List<ApprovalDeviceDto> baseList = loadPendingApprovalDtos();

        Set<String> categorySet = new HashSet<>();
        Set<String> applicantSet = new HashSet<>();
        baseList.forEach(dto -> {
            if (dto.getCategoryName() != null && !dto.getCategoryName().isBlank()) {
                categorySet.add(dto.getCategoryName());
            }
            if (dto.getUserName() != null && !dto.getUserName().isBlank()) {
                applicantSet.add(dto.getUserName());
            }
        });

        String normalizedFilterField = normalizeApprovalFilterField(request.filterField());
        String normalizedKeyword = normalizeKeyword(request.keyword());
        String normalizedChip = normalizeChipValue(request.chipValue());

        List<ApprovalDeviceDto> filtered = baseList.stream()
                .filter(dto -> matchesChip(dto, normalizedFilterField, normalizedChip))
                .filter(dto -> matchesKeyword(dto, normalizedFilterField, normalizedKeyword))
                .sorted(buildApprovalComparator(request.sortField(), request.sortOrder()))
                .collect(Collectors.toList());

        int size = clampSize(request.size());
        int totalElements = filtered.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalElements / size));
        int page = clampPage(request.page(), totalPages, totalElements);
        int fromIndex = Math.min((page - 1) * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<ApprovalDeviceDto> content = filtered.subList(fromIndex, toIndex);

        Collator collator = Collator.getInstance(Locale.KOREAN);
        collator.setStrength(Collator.PRIMARY);

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("categories", categorySet.stream().sorted(collator).toList());
        metadata.put("applicants", applicantSet.stream().sorted(collator).toList());

        return PageResponse.of(content, page, size, totalElements, totalPages, metadata);
    }

    @Transactional(readOnly = true)
    public List<ApprovalDeviceDto> findApprovalsForUser(String username) {
        CommonLookupService.KeycloakUserInfo userInfo = resolveUserInfo(username);
        if (isAdminUser(username, userInfo)) {
            return approvalRequestRepository.findByCategoryOrderBySubmittedAtDesc(ApprovalCategory.DEVICE)
                    .stream()
                    .map(ApprovalDeviceDto::new)
                    .toList();
        }

        UUID targetExternalId = Optional.ofNullable(userInfo)
                .map(CommonLookupService.KeycloakUserInfo::id)
                .orElseGet(() -> commonLookupService.currentUserIdFromJwt()
                        .map(this::safeUuid)
                        .orElse(null));

        List<ApprovalRequest> approvals;
        if (targetExternalId != null) {
            approvals = approvalRequestRepository
                    .findByCategoryAndRequesterExternalIdOrderBySubmittedAtDesc(ApprovalCategory.DEVICE, targetExternalId);
        } else {
            approvals = approvalRequestRepository.findByCategoryOrderBySubmittedAtDesc(ApprovalCategory.DEVICE).stream()
                    .filter(request -> isRequester(request, userInfo, username))
                    .toList();
        }

        return approvals.stream()
                .map(ApprovalDeviceDto::new)
                .toList();
    }

    @Transactional
    public void cancelApproval(Long approvalId, String username) {
        ApprovalRequest approval = approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.APPROVAL_NOT_FOUND,
                        "Approval not found: " + approvalId));

        ApprovalStatus status = approval.getStatus();
        if (status == ApprovalStatus.APPROVED || status == ApprovalStatus.REJECTED || status == ApprovalStatus.CANCELLED) {
            throw new CustomException(CustomErrorCode.APPROVAL_ALREADY_COMPLETED);
        }

        CommonLookupService.KeycloakUserInfo userInfo = resolveUserInfo(username);
        if (!isAdminUser(username, userInfo) && !isRequester(approval, userInfo, username)) {
            throw new CustomException(CustomErrorCode.APPROVAL_CANCEL_FORBIDDEN);
        }

        DeviceApprovalDetail detail = approval.getDetail() instanceof DeviceApprovalDetail deviceDetail
                ? deviceDetail
                : null;
        if (detail != null) {
            Devices device = detail.getDevice();
            if (device != null) {
                if (detail.getAction() == DeviceApprovalAction.RENTAL) {
                    device.setIsUsable(Boolean.TRUE);
                    UUID requesterId = approval.getRequesterExternalId();
                    if (requesterId != null && requesterId.equals(device.getUserUuid())) {
                        device.setUserUuid(null);
                    }
                    String requesterName = Optional.ofNullable(approval.getRequesterName()).map(String::trim).orElse(null);
                    if (requesterName != null && requesterName.equals(device.getRealUser())) {
                        device.setRealUser(null);
                    }
                }
                devicesRepository.save(device);
            }
        }

        approval.markCancelled();
        approvalRequestRepository.save(approval);
    }

    @Transactional
    public ApprovalDeviceDto updateApplication(Long approvalId, ApprovalUpdateRequest updateRequest) {
        if (updateRequest == null) {
            throw new CustomException(CustomErrorCode.APPROVAL_UPDATE_EMPTY);
        }

        String username = Optional.ofNullable(updateRequest.getUsername())
                .map(String::trim)
                .orElse(null);

        ApprovalRequest approval = approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new CustomException(CustomErrorCode.APPROVAL_NOT_FOUND,
                        "Approval not found: " + approvalId));

        ApprovalStatus status = approval.getStatus();
        if (status == ApprovalStatus.APPROVED || status == ApprovalStatus.REJECTED || status == ApprovalStatus.CANCELLED) {
            throw new CustomException(CustomErrorCode.APPROVAL_UPDATE_COMPLETED);
        }

        CommonLookupService.KeycloakUserInfo userInfo = resolveUserInfo(username);
        if (!isRequester(approval, userInfo, username) && !isAdminUser(username, userInfo)) {
            throw new CustomException(CustomErrorCode.APPROVAL_UPDATE_FORBIDDEN);
        }

        String trimmedReason = Optional.ofNullable(updateRequest.getReason())
                .map(String::trim)
                .orElse(approval.getReason() != null ? approval.getReason().trim() : "");
        if (trimmedReason.isEmpty()) {
            throw new CustomException(CustomErrorCode.APPROVAL_UPDATE_REASON_REQUIRED);
        }
        approval.setReason(trimmedReason);

        if (updateRequest.getDeadline() != null) {
            approval.setDueDate(updateRequest.getDeadline());
        }

        DeviceApprovalDetail detail = approval.getDetail() instanceof DeviceApprovalDetail deviceDetail
                ? deviceDetail
                : null;
        if (detail == null) {
            detail = DeviceApprovalDetail.create();
            detail.attachTo(approval);
        }

        detail.setRequestedRealUser(Optional.ofNullable(updateRequest.getRealUser())
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .orElse(null));

        String departmentName = Optional.ofNullable(updateRequest.getDepartmentName())
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse(null);
        Departments requestedDepartment = lookupDepartment(departmentName);
        if (departmentName != null && requestedDepartment == null) {
            throw new CustomException(CustomErrorCode.REQUESTED_DEPARTMENT_NOT_FOUND);
        }
        detail.setRequestedDepartment(requestedDepartment);

        String projectIdentifier = firstNonBlank(updateRequest.getProjectName(), updateRequest.getProjectCode());
        Projects requestedProject = lookupProject(projectIdentifier);
        if (projectIdentifier != null && requestedProject == null) {
            throw new CustomException(CustomErrorCode.PROJECT_NOT_FOUND);
        }
        detail.setRequestedProject(requestedProject);

        if (updateRequest.getUsageStartDate() != null && updateRequest.getUsageEndDate() != null
                && updateRequest.getUsageEndDate().isBefore(updateRequest.getUsageStartDate())) {
            throw new CustomException(CustomErrorCode.APPROVAL_USAGE_END_BEFORE_START);
        }
        detail.setRequestedUsageStartDate(updateRequest.getUsageStartDate());
        detail.setRequestedUsageEndDate(updateRequest.getUsageEndDate());

        if (detail.getAction() == DeviceApprovalAction.RETURN && updateRequest.getTags() != null) {
            Devices taggedDevice = detail.getDevice();
            if (taggedDevice != null) {
                tagsService.replaceDeviceTags(taggedDevice, updateRequest.getTags());
            }
        }

        ApprovalRequest saved = approvalRequestRepository.save(approval);
        saved.getSteps().stream()
            .filter(step -> step.getSequence() == 1)
            .findFirst()
            .ifPresent(step -> mailService.sendApprovalUpdatedMail(
                saved,
                step,
                buildApprovalLink(saved.getId(), LinkAudience.APPROVER)));
        return new ApprovalDeviceDto(saved);
    }

    private List<ApprovalDeviceDto> loadPendingApprovalDtos() {
        List<ApprovalStatus> statuses = Arrays.stream(ApprovalStatus.values())
                .toList();

        List<ApprovalRequest> approvals = approvalRequestRepository.findByCategoryAndStatusIn(
                ApprovalCategory.DEVICE,
                statuses);

    approvals.sort(Comparator.comparing(
        (ApprovalRequest request) -> Optional.ofNullable(request.getSubmittedAt())
            .orElse(request.getCreatedDate()),
        Comparator.nullsLast(Comparator.naturalOrder())));

        return approvals.stream()
                .map(ApprovalDeviceDto::new)
                .toList();
    }

    private String normalizeApprovalFilterField(String raw) {
        if (raw == null || raw.isBlank()) {
            return "categoryName";
        }
        return switch (raw) {
            case "신청번호", "approvalId" -> "approvalId";
            case "신청장비", "categoryName" -> "categoryName";
            case "신청자", "userName" -> "userName";
            case "신청정보", "approvalInfo" -> "approvalInfo";
            case "관리번호", "deviceId" -> "deviceId";
            default -> "categoryName";
        };
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeChipValue(String chipValue) {
        if (chipValue == null) {
            return null;
        }
        String trimmed = chipValue.trim();
        if (trimmed.isEmpty() || "ALL".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }

    private boolean matchesChip(ApprovalDeviceDto dto, String filterField, String chipValue) {
        if (chipValue == null) {
            return true;
        }
        return switch (filterField) {
            case "categoryName" -> chipValue.equals(dto.getCategoryName());
            case "userName" -> chipValue.equals(dto.getUserName());
            case "approvalInfo" -> {
                ApprovalStatus targetStatus = ApprovalStatus.fromDisplayName(chipValue);
                if (targetStatus != null && dto.getApprovalStatus() != null) {
                    yield dto.getApprovalStatus() == targetStatus;
                }
                yield chipValue.equals(dto.getApprovalInfo());
            }
            default -> true;
        };
    }

    private boolean matchesKeyword(ApprovalDeviceDto dto, String filterField, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        return switch (filterField) {
            case "approvalId" -> containsIgnoreCase(String.valueOf(dto.getApprovalId()), lowerKeyword);
            case "categoryName" -> containsIgnoreCase(dto.getCategoryName(), lowerKeyword);
            case "userName" -> containsIgnoreCase(dto.getUserName(), lowerKeyword);
            case "deviceId" -> containsIgnoreCase(dto.getDeviceId(), lowerKeyword);
            case "approvalInfo" -> {
                String combined = ((dto.getType() == null ? "" : dto.getType()) + " "
                        + (dto.getApprovalInfo() == null ? "" : dto.getApprovalInfo())).trim();
                yield containsIgnoreCase(combined, lowerKeyword);
            }
            default -> true;
        };
    }

    private Comparator<ApprovalDeviceDto> buildApprovalComparator(String sortFieldRaw, String sortOrderRaw) {
        String sortField = normalizeSortField(sortFieldRaw);
        boolean desc = "desc".equalsIgnoreCase(sortOrderRaw);

        Comparator<ApprovalDeviceDto> statusComparator = Comparator
                .comparingInt(dto -> statusPriority(dto.getApprovalStatus()));
        Comparator<ApprovalDeviceDto> submissionComparator = Comparator.comparing(
                (ApprovalDeviceDto dto) -> resolveSubmittedAt(dto),
                Comparator.nullsLast(Comparator.reverseOrder()));
        Comparator<ApprovalDeviceDto> idComparator = Comparator.comparingLong(
                dto -> Optional.ofNullable(dto.getApprovalId()).orElse(Long.MAX_VALUE));
        Comparator<ApprovalDeviceDto> defaultTieBreaker = statusComparator
                .thenComparing(submissionComparator)
                .thenComparing(idComparator);

        if ("submittedAt".equals(sortField)) {
            Comparator<ApprovalDeviceDto> orderedSubmission = desc
                    ? submissionComparator.reversed()
                    : submissionComparator;
            return statusComparator
                    .thenComparing(orderedSubmission)
                    .thenComparing(idComparator);
        }

        Comparator<ApprovalDeviceDto> fieldComparator = switch (sortField) {
            case "approvalId" -> idComparator;
            case "deviceId" -> Comparator.comparing(
                    dto -> defaultString(dto.getDeviceId()),
                    localeAwareStringComparator());
            case "deadline" -> Comparator.comparing(
                    ApprovalDeviceDto::getDeadline,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "type" -> Comparator.comparing(
                    dto -> defaultString(dto.getType()),
                    localeAwareStringComparator());
            case "approvalStatus" -> Comparator.comparingInt(dto -> statusPriority(dto.getApprovalStatus()));
            default -> submissionComparator;
        };

        if (desc) {
            fieldComparator = fieldComparator.reversed();
        }

        return fieldComparator.thenComparing(defaultTieBreaker);
    }

    private String normalizeSortField(String raw) {
        if (raw == null || raw.isBlank()) {
            return "submittedAt";
        }
        return switch (raw) {
            case "approvalId", "신청번호" -> "approvalId";
            case "deviceId", "관리번호" -> "deviceId";
            case "deadline", "마감일" -> "deadline";
            case "createdDate", "submittedAt", "등록일", "접수일" -> "submittedAt";
            case "type", "구분" -> "type";
            case "approvalStatus", "status", "상태" -> "approvalStatus";
            default -> "submittedAt";
        };
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 10;
        }
        return Math.min(size, 100);
    }

    private int clampPage(int page, int totalPages, int totalElements) {
        if (totalElements == 0) {
            return 1;
        }
        if (page <= 0) {
            return 1;
        }
        return Math.min(page, Math.max(totalPages, 1));
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        if (value == null) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private int statusPriority(ApprovalStatus status) {
        if (status == null) {
            return 5;
        }
        return switch (status) {
            case PENDING, IN_PROGRESS -> 0;
            case APPROVED -> 1;
            case REJECTED, CANCELLED -> 2;
        };
    }

    private LocalDateTime resolveSubmittedAt(ApprovalDeviceDto dto) {
        if (dto == null) {
            return null;
        }
        return Optional.ofNullable(dto.getSubmittedAt())
                .orElse(dto.getCreatedDate());
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private Comparator<String> localeAwareStringComparator() {
        Collator collator = Collator.getInstance(Locale.KOREAN);
        collator.setStrength(Collator.PRIMARY);
        return (left, right) -> collator.compare(left, right);
    }

    private boolean isAdminUser(String username, CommonLookupService.KeycloakUserInfo userInfo) {
        if (userInfo != null && userInfo.username() != null && commonLookupService.isAdminUser(userInfo.username())) {
            return true;
        }
        if (username != null && !username.isBlank() && commonLookupService.isAdminUser(username.trim())) {
            return true;
        }
        return commonLookupService.currentUsernameFromJwt()
                .map(commonLookupService::isAdminUser)
                .orElse(false);
    }

    private boolean isRequester(ApprovalRequest approval,
                                 CommonLookupService.KeycloakUserInfo userInfo,
                                 String username) {
        if (approval == null) {
            return false;
        }
        UUID requesterExternalId = approval.getRequesterExternalId();
        if (userInfo != null && userInfo.id() != null && requesterExternalId != null
                && requesterExternalId.equals(userInfo.id())) {
            return true;
        }
        if (requesterExternalId != null) {
            UUID currentUserId = commonLookupService.currentUserIdFromJwt()
                    .map(this::safeUuid)
                    .orElse(null);
            if (currentUserId != null && requesterExternalId.equals(currentUserId)) {
                return true;
            }
        }

        String requesterName = Optional.ofNullable(approval.getRequesterName())
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .orElse(null);
        if (requesterName == null) {
            return false;
        }

        if (userInfo != null) {
            String displayName = Optional.ofNullable(userInfo.displayName())
                    .map(String::trim)
                    .orElse(null);
            if (displayName != null && requesterName.equalsIgnoreCase(displayName)) {
                return true;
            }
            String normalizedUsername = safeUsername(userInfo, username);
            if (normalizedUsername != null && requesterName.equalsIgnoreCase(normalizedUsername)) {
                return true;
            }
        }

        if (username != null && !username.isBlank() && requesterName.equalsIgnoreCase(username.trim())) {
            return true;
        }

        return false;
    }

    @Transactional(readOnly = true)
    public ApprovalDeviceDto findDetail(Long approvalId) {
        ApprovalRequest approval = approvalRequestRepository.findById(approvalId)
        .orElseThrow(() -> new CustomException(CustomErrorCode.APPROVAL_NOT_FOUND,
            "Approval not found: " + approvalId));
        return new ApprovalDeviceDto(approval);
    }

    private void buildApprovalSteps(ApprovalRequest approval, List<String> approverUsernames) {
        if (approverUsernames == null || approverUsernames.isEmpty()) {
            return;
        }
        int sequence = 1;
        for (String username : approverUsernames) {
            CommonLookupService.KeycloakUserInfo approverInfo = resolveUserInfo(username);
            String normalizedUsername = safeUsername(approverInfo, username);
            if (normalizedUsername == null) {
                throw new CustomException(CustomErrorCode.APPROVER_USERNAME_REQUIRED);
            }
            ApprovalStep step = ApprovalStep.builder()
                    .sequence(sequence++)
                    .build();
            applyApproverMetadata(step, approverInfo, normalizedUsername);
            approval.addStep(step);
        }
    }

    private List<String> prepareApproverSequence(List<String> requestedApprovers, boolean fallbackToDefault) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        Optional.ofNullable(requestedApprovers)
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .forEach(unique::add);

        if (!unique.isEmpty() || !fallbackToDefault) {
            return new ArrayList<>(unique);
        }

        LinkedHashSet<String> defaults = approvalProperties.getDefaultApprovers().stream()
                .sorted(Comparator.comparingInt(ApprovalProperties.Stage::getStage))
                .map(stage -> {
                    String username = stage.getUsername();
                    if (username != null && !username.isBlank()) {
                        return username.trim();
                    }
                    String displayName = stage.getDisplayName();
                    return displayName != null ? displayName.trim() : null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return new ArrayList<>(defaults);
    }

    private void applyDeviceStateOnSubmission(Devices device,
                                              DeviceApplicationRequestDto request,
                                              DeviceApprovalAction action) {
        if (action != DeviceApprovalAction.RENTAL && request.getIsUsable() != null) {
            device.setIsUsable(request.getIsUsable());
        }

        if (action != DeviceApprovalAction.DISPOSAL
                && request.getStatus() != null
                && !request.getStatus().isBlank()) {
            device.setStatus(request.getStatus());
        }

        String requestedRealUser = Optional.ofNullable(request.getRealUser())
                .map(String::trim)
                .orElse(null);
        if (requestedRealUser != null) {
            device.setRealUser(requestedRealUser.isEmpty() ? null : requestedRealUser);
        }

        devicesRepository.save(device);
    }

    private void validateDuplicateDeviceAction(Devices device, DeviceApprovalAction requestedAction) {
        if (device == null || requestedAction == null) {
            return;
        }
        if (requestedAction != DeviceApprovalAction.RETURN && requestedAction != DeviceApprovalAction.DISPOSAL) {
            return;
        }

    List<DeviceApprovalDetail> activeDetails = deviceApprovalDetailRepository.findActiveByDeviceIdAndStatuses(
        device.getId(),
        List.of(ApprovalStatus.PENDING, ApprovalStatus.IN_PROGRESS));

        boolean hasBlocking = activeDetails.stream()
                .map(DeviceApprovalDetail::getAction)
                .filter(Objects::nonNull)
                .anyMatch(action -> action == DeviceApprovalAction.RETURN || action == DeviceApprovalAction.DISPOSAL);

        if (hasBlocking) {
            throw new CustomException(CustomErrorCode.APPROVAL_PENDING_DUPLICATE);
        }
    }

    private void applyDeviceStateOnCompletion(ApprovalRequest approval) {
        if (approval == null || !(approval.getDetail() instanceof DeviceApprovalDetail detail)) {
            return;
        }

        Devices device = detail.getDevice();
        DeviceApprovalAction action = detail.getAction();
        if (device == null || action == null) {
            return;
        }

        switch (action) {
            case RENTAL -> applyRentalCompletion(device, approval, detail);
            case RETURN -> applyReturnCompletion(device, detail);
            default -> {
                // fall through to apply requested attributes below
            }
        }

        applyRequestedAttributes(device, detail);
        devicesRepository.save(device);
    }

    private void applyRentalCompletion(Devices device,
                                       ApprovalRequest approval,
                                       DeviceApprovalDetail detail) {
        device.setIsUsable(Boolean.FALSE);

    UUID resolvedUuid = Optional.ofNullable(approval.getRequesterExternalId()).orElse(null);
    if (resolvedUuid == null) {
        resolvedUuid = Optional.ofNullable(approval.getRequesterName())
            .filter(name -> !name.isBlank())
            .flatMap(commonLookupService::resolveKeycloakUserIdByUsername)
            .map(this::safeUuid)
            .orElse(null);
    }

    device.setUserUuid(resolvedUuid);

    if ((detail.getRequestedRealUser() == null || detail.getRequestedRealUser().isBlank())) {
        String requesterName = Optional.ofNullable(approval.getRequesterName())
            .filter(name -> !name.isBlank())
            .orElse(null);
        if (requesterName != null) {
        device.setRealUser(requesterName);
        }
    }
    }

    private void applyReturnCompletion(Devices device, DeviceApprovalDetail detail) {
    device.setIsUsable(Boolean.TRUE);
    device.setUserUuid(null);
    device.setRealUser(null);
    }

    private void applyRequestedAttributes(Devices device, DeviceApprovalDetail detail) {
        if (detail.getRequestedStatus() != null && !detail.getRequestedStatus().isBlank()) {
            device.setStatus(detail.getRequestedStatus());
        }
        if (detail.getRequestedPurpose() != null && !detail.getRequestedPurpose().isBlank()) {
            device.setPurpose(detail.getRequestedPurpose());
        }
        if (detail.getRequestedProject() != null) {
            device.setProjectId(detail.getRequestedProject());
        }
        if (detail.getRequestedDepartment() != null) {
            device.setManageDep(detail.getRequestedDepartment());
        }
        if (detail.getAction() != DeviceApprovalAction.RETURN
                && detail.getRequestedRealUser() != null
                && !detail.getRequestedRealUser().isBlank()) {
            device.setRealUser(detail.getRequestedRealUser());
        }
    }

    private void ensurePreviousStepsApproved(ApprovalRequest approval, int currentSequence) {
        if (currentSequence <= 1) {
            return;
        }
        boolean previousApproved = approval.getSteps().stream()
                .filter(step -> step.getSequence() < currentSequence)
                .allMatch(step -> step.getStatus() == StepStatus.APPROVED);
        if (!previousApproved) {
            throw new CustomException(CustomErrorCode.APPROVAL_PREVIOUS_STEP_INCOMPLETE);
        }
    }

    private void rollbackSubsequentSteps(ApprovalRequest approval, int currentSequence) {
        if (approval == null || approval.getSteps() == null || approval.getSteps().isEmpty()) {
            return;
        }
        approval.getSteps().stream()
                .filter(step -> step.getSequence() > currentSequence)
                .forEach(ApprovalStep::reset);
    }

    private ApprovalStep findNextStep(ApprovalRequest approval, int currentSequence) {
        return approval.getSteps().stream()
                .filter(step -> step.getSequence() > currentSequence)
                .sorted((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
                .findFirst()
                .orElse(null);
    }

    private void notifyApprover(ApprovalRequest approval, ApprovalStep step, String message) {
        String targetReceiver = Optional.ofNullable(step.getApproverName())
            .filter(name -> !name.isBlank())
            .orElse(null);
        if (targetReceiver == null || targetReceiver.isBlank()) {
            return;
        }
        String subjectPrefix = (message != null && !message.isBlank()) ? message : "[장비 결재 요청]";
        String subject = subjectPrefix + " " + buildApprovalSubject(approval);
        String link = buildApprovalLink(approval.getId(), LinkAudience.APPROVER);
        notificationService.createNotification(targetReceiver, subject,
            Constants.NOTIFICATION_APPROVAL, link);
        mailService.sendApprovalAwaitingMail(approval, step, link, message);
    }

    private void notifyApplicantOnCompletion(ApprovalRequest approval) {
        String targetReceiver = Optional.ofNullable(approval.getRequesterName())
            .filter(name -> !name.isBlank())
            .orElse(null);
        if (targetReceiver == null || targetReceiver.isBlank()) {
            return;
        }
        String subject = "[장비 결재 완료] " + buildApprovalSubject(approval);
        String link = buildApprovalLink(approval.getId(), LinkAudience.APPLICANT);
        notificationService.createNotification(targetReceiver, subject,
            Constants.NOTIFICATION_APPROVAL, link);
        mailService.sendApprovalCompletedMail(approval, link);
    }

    private void notifyApplicantOnRejection(ApprovalRequest approval,
                                            CommonLookupService.KeycloakUserInfo approverInfo,
                                            String approverUsername,
                                            String rejectionReason) {
        String subject = "[장비 결재 반려] " + buildApprovalSubject(approval);

        String requesterReceiver = Optional.ofNullable(approval.getRequesterName())
            .filter(name -> !name.isBlank())
            .orElse(null);
        if (requesterReceiver != null && !requesterReceiver.isBlank()) {
            String link = buildApprovalLink(approval.getId(), LinkAudience.APPLICANT);
            notificationService.createNotification(requesterReceiver, subject,
                Constants.NOTIFICATION_APPROVAL, link);
            mailService.sendApprovalRejectedMail(approval, rejectionReason, link);
        }

        String approverReceiver = Optional.ofNullable(safeUsername(approverInfo, approverUsername))
            .orElse(approverUsername);
        if (approverReceiver != null && !approverReceiver.isBlank()) {
            notificationService.createNotification(approverReceiver, subject,
                Constants.NOTIFICATION_APPROVAL, buildApprovalLink(approval.getId(), LinkAudience.APPROVER));
        }
    }

    private void notifyApplicantOnProgress(ApprovalRequest approval, ApprovalStep completedStep) {
    String targetReceiver = Optional.ofNullable(approval.getRequesterName())
        .filter(name -> !name.isBlank())
        .orElse(null);
        if (targetReceiver == null || targetReceiver.isBlank()) {
            return;
        }
        int sequence = completedStep != null ? completedStep.getSequence() : 0;
        String stepLabel = sequence > 0 ? sequence + "차 결재가 완료되었습니다." : "결재가 진행 중입니다.";
        String subject = "[장비 결재 진행] " + buildApprovalSubject(approval) + " - " + stepLabel;
    notificationService.createNotification(targetReceiver, subject,
    Constants.NOTIFICATION_APPROVAL, buildApprovalLink(approval.getId(), LinkAudience.APPLICANT));
    }

    private String buildApprovalSubject(ApprovalRequest approval) {
        DeviceApprovalDetail detail = approval.getDetail() instanceof DeviceApprovalDetail deviceDetail
                ? deviceDetail
                : null;
        String deviceId = Optional.ofNullable(detail)
                .map(DeviceApprovalDetail::getDevice)
                .map(Devices::getId)
                .orElse("");
        if (!deviceId.isBlank()) {
            return deviceId;
        }
        return Optional.ofNullable(approval.getTitle())
                .filter(title -> !title.isBlank())
                .orElse("승인 ID " + approval.getId());
    }

    private enum LinkAudience {
        APPROVER,
        APPLICANT
    }

    private String buildApprovalLink(Long approvalId, LinkAudience audience) {
        String listPath = audience == LinkAudience.APPROVER ? "/admin/approvals" : "/mypage/requests";
        if (approvalId == null) {
            return listPath;
        }
        return listPath + "/" + approvalId;
    }

    private String buildTitle(DeviceApprovalAction action, Devices device) {
        String deviceId = device != null ? device.getId() : null;
        if (deviceId == null || deviceId.isBlank()) {
            return action.getDisplayName();
        }
        return action.getDisplayName() + " - " + deviceId;
    }

    private Optional<ApprovalStep> resolveApprovalStep(Long approvalId,
                                                       String approverUsername,
                                                       UUID approverExternalId) {
        if (approverExternalId != null) {
            Optional<ApprovalStep> step = approvalStepRepository.findByRequestIdAndApproverExternalId(approvalId, approverExternalId);
            if (step.isPresent()) {
                return step;
            }
        }
        String normalized = approverUsername != null ? approverUsername.trim() : null;
        if (normalized == null || normalized.isEmpty()) {
            return Optional.empty();
        }
        return approvalStepRepository.findByRequestIdAndApproverName(approvalId, normalized);
    }

    private void applyApproverMetadata(ApprovalStep step,
                                       CommonLookupService.KeycloakUserInfo approverInfo,
                                       String fallbackUsername) {
        if (step == null) {
            return;
        }
        if (approverInfo != null) {
            if (step.getApproverName() == null || step.getApproverName().isBlank()) {
                step.setApproverName(safeUsername(approverInfo, fallbackUsername));
            }
            if ((step.getApproverEmail() == null || step.getApproverEmail().isBlank())
                    && approverInfo.email() != null && !approverInfo.email().isBlank()) {
                step.setApproverEmail(approverInfo.email());
            }
            if (approverInfo.id() != null && step.getApproverExternalId() == null) {
                step.setApproverExternalId(approverInfo.id());
            }
        }
        if ((step.getApproverName() == null || step.getApproverName().isBlank()) && fallbackUsername != null) {
            step.setApproverName(fallbackUsername);
        }
    }

    private UUID safeUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Projects lookupProject(String projectIdentifier) {
        if (projectIdentifier == null || projectIdentifier.isBlank()) {
            return null;
        }
        Projects project = projectsRepository.findByName(projectIdentifier);
        if (project != null) {
            return project;
        }
        return projectsRepository.findByCode(projectIdentifier).orElse(null);
    }

    private Departments lookupDepartment(String departmentName) {
        if (departmentName == null || departmentName.isBlank()) {
            return null;
        }
        return departmentsRepository.findByName(departmentName).orElse(null);
    }

    private CommonLookupService.KeycloakUserInfo resolveUserInfo(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }

        String normalized = username.trim();
        Optional<ApprovalProperties.Stage> defaultStage = findDefaultApproverStage(normalized);
        if (defaultStage.isPresent()) {
            ApprovalProperties.Stage stage = defaultStage.get();
            UUID stageUuid = stage.getKeycloakId();
            String resolvedUsername = firstNonBlank(stage.getUsername(), stage.getDisplayName(), normalized);
            String displayName = firstNonBlank(stage.getDisplayName(), stage.getUsername(), normalized);
            String stageEmail = stage.getEmail();
            if (stageEmail != null && !stageEmail.isBlank()) {
                return new CommonLookupService.KeycloakUserInfo(stageUuid, resolvedUsername, stageEmail.trim(), displayName);
            }

            CommonLookupService.KeycloakUserInfo infoById = stageUuid != null
                    ? commonLookupService.resolveKeycloakUserInfoById(stageUuid).orElse(null)
                    : null;
            if (infoById != null && infoById.email() != null && !infoById.email().isBlank()) {
                return infoById;
            }

            String stageUsername = stage.getUsername();
            if (stageUsername != null && !stageUsername.isBlank()) {
                CommonLookupService.KeycloakUserInfo infoByUsername = commonLookupService.resolveKeycloakUserInfoByUsername(stageUsername.trim())
                        .orElse(null);
                if (infoByUsername != null) {
                    return infoByUsername;
                }
            }

            return commonLookupService.resolveKeycloakUserInfoByUsername(normalized)
                    .orElseGet(() -> commonLookupService.fallbackUserInfo(resolvedUsername));
        }

        return commonLookupService.resolveKeycloakUserInfoByUsername(normalized)
                .orElseGet(() -> commonLookupService.fallbackUserInfo(normalized));
    }

    private void applyRequesterMetadata(ApprovalRequest approval,
                                        CommonLookupService.KeycloakUserInfo requesterInfo,
                                        String fallbackUsername) {
        if (approval == null) {
            return;
        }
        CommonLookupService.KeycloakUserInfo effectiveRequester = requesterInfo;

        UUID resolvedExternalId = Optional.ofNullable(effectiveRequester)
                .map(CommonLookupService.KeycloakUserInfo::id)
                .orElse(null);

        if (resolvedExternalId == null) {
            resolvedExternalId = commonLookupService.currentUserIdFromJwt()
                    .map(this::safeUuid)
                    .orElse(null);
        }

        if (resolvedExternalId == null && fallbackUsername != null) {
            resolvedExternalId = findDefaultApproverStage(fallbackUsername)
                    .map(ApprovalProperties.Stage::getKeycloakId)
                    .orElse(null);
        }

        if (resolvedExternalId == null && effectiveRequester != null) {
            UUID candidateId = findDefaultApproverStage(effectiveRequester.username())
                    .map(ApprovalProperties.Stage::getKeycloakId)
                    .orElse(null);
            if (candidateId == null) {
                candidateId = findDefaultApproverStage(effectiveRequester.displayName())
                        .map(ApprovalProperties.Stage::getKeycloakId)
                        .orElse(null);
            }
            resolvedExternalId = candidateId;
        }

        if (resolvedExternalId == null) {
            throw new CustomException(CustomErrorCode.APPROVAL_REQUESTER_UUID_MISSING);
        }

        approval.setRequesterExternalId(resolvedExternalId);

        if (effectiveRequester == null || effectiveRequester.id() == null || !resolvedExternalId.equals(effectiveRequester.id())) {
            effectiveRequester = commonLookupService.resolveKeycloakUserInfoById(resolvedExternalId)
                    .orElse(effectiveRequester);
        }

        if (effectiveRequester == null && fallbackUsername != null && !fallbackUsername.isBlank()) {
            effectiveRequester = resolveUserInfo(fallbackUsername);
        }

        if (effectiveRequester != null) {
            String displayName = Optional.ofNullable(effectiveRequester.displayName())
                    .map(String::trim)
                    .filter(name -> !name.isBlank())
                    .orElse(null);

            if (displayName != null) {
                approval.setRequesterName(displayName);
            } else {
                String resolvedName = safeUsername(effectiveRequester, fallbackUsername);
                if (resolvedName != null && !resolvedName.isBlank()) {
                    approval.setRequesterName(resolvedName);
                }
            }

            if (effectiveRequester.email() != null && !effectiveRequester.email().isBlank()) {
                approval.setRequesterEmail(effectiveRequester.email());
            }
        }

        if ((approval.getRequesterName() == null || approval.getRequesterName().isBlank())
                && fallbackUsername != null && !fallbackUsername.isBlank()) {
            approval.setRequesterName(fallbackUsername.trim());
        }
    }

    private String safeUsername(CommonLookupService.KeycloakUserInfo userInfo, String fallbackUsername) {
        if (userInfo != null && userInfo.username() != null && !userInfo.username().isBlank()) {
            return userInfo.username().trim();
        }
        if (fallbackUsername == null) {
            return null;
        }
        String trimmed = fallbackUsername.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Optional<ApprovalProperties.Stage> findDefaultApproverStage(String candidate) {
        if (candidate == null) {
            return Optional.empty();
        }
        String normalized = candidate.trim();
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return approvalProperties.getDefaultApprovers().stream()
                .filter(stage -> matchesCandidate(normalized, stage.getUsername())
                        || matchesCandidate(normalized, stage.getDisplayName()))
                .findFirst();
    }

    private boolean matchesCandidate(String normalized, String value) {
        return value != null && !value.isBlank() && normalized.equalsIgnoreCase(value.trim());
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null) {
                String trimmed = value.trim();
                if (!trimmed.isBlank()) {
                    return trimmed;
                }
            }
        }
        return null;
    }

}
