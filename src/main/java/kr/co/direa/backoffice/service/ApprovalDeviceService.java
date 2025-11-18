package kr.co.direa.backoffice.service;

import java.text.Collator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import kr.co.direa.backoffice.domain.DeviceApprovalItem;
import kr.co.direa.backoffice.domain.Devices;
import kr.co.direa.backoffice.domain.Projects;
import kr.co.direa.backoffice.domain.enums.ApprovalCategory;
import kr.co.direa.backoffice.domain.enums.ApprovalStatus;
import kr.co.direa.backoffice.domain.enums.DeviceApprovalAction;
import kr.co.direa.backoffice.domain.enums.StepStatus;
import kr.co.direa.backoffice.domain.enums.RealUserMode;
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
    private static final String DEFAULT_METADATA_PROJECT_NAME = "본사";
    private static final String DEFAULT_METADATA_DEPARTMENT_NAME = "경영지원부";

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

        List<String> requestedDeviceIds = new ArrayList<>();
        if (request.getDeviceIds() != null) {
            request.getDeviceIds().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .forEach(requestedDeviceIds::add);
        }
        if (requestedDeviceIds.isEmpty() && request.getDeviceId() != null && !request.getDeviceId().isBlank()) {
            requestedDeviceIds.add(request.getDeviceId().trim());
        }

        LinkedHashSet<String> uniqueDeviceIds = new LinkedHashSet<>(requestedDeviceIds);
        if (uniqueDeviceIds.isEmpty()) {
            throw new CustomException(CustomErrorCode.DEVICE_ID_REQUIRED);
        }

        List<Devices> targetDevices = devicesRepository.findAllById(uniqueDeviceIds);
        if (targetDevices.size() != uniqueDeviceIds.size()) {
            Set<String> foundIds = targetDevices.stream()
                    .map(Devices::getId)
                    .collect(Collectors.toSet());
            String missing = uniqueDeviceIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .findFirst()
                    .orElse("unknown");
            throw new CustomException(CustomErrorCode.DEVICE_NOT_FOUND,
                    "Device not found: " + missing);
        }

    CommonLookupService.KeycloakUserInfo requesterInfo = resolveUserInfo(request.getUserName());
    String currentUsername = commonLookupService.currentUsernameFromJwt().orElse(null);
    String currentDisplayName = commonLookupService.currentUserDisplayNameFromJwt().orElse(null);
    String applicantAutoRealUser = firstNonBlank(
        currentDisplayName,
        currentUsername,
        requesterInfo != null ? requesterInfo.displayName() : null,
        requesterInfo != null ? requesterInfo.username() : null,
        request.getUserName());

        validateDuplicateDeviceAction(targetDevices, action);

        Map<String, DeviceApplicationRequestDto.DeviceSelection> perDeviceSelections =
                toDeviceSelectionMap(request.getDevices());
        List<String> defaultReturnTags = sanitizeTags(request.getTag());

        if (action == DeviceApprovalAction.RETURN) {
            for (Devices device : targetDevices) {
                if (device == null || device.getId() == null) {
                    continue;
                }
                DeviceApplicationRequestDto.DeviceSelection selection = perDeviceSelections.get(device.getId());
                List<String> resolvedTags = (selection == null)
                        ? defaultReturnTags
                        : sanitizeTags(selection.getTags());
                tagsService.replaceDeviceTags(device, resolvedTags);
            }
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

    String defaultRealUser = applicantAutoRealUser;
        Map<String, DeviceApprovalDetail.RequestedOverrides> requestedOverrides = buildRequestedOverrides(
                uniqueDeviceIds,
                request,
                requestedProject,
                requestedDepartment,
        defaultRealUser,
                perDeviceSelections);

    applyDeviceStateOnSubmission(targetDevices, request, action, perDeviceSelections, requestedOverrides);

        String primaryDeviceId = uniqueDeviceIds.isEmpty() ? null : uniqueDeviceIds.iterator().next();
        DeviceApprovalDetail detail = DeviceApprovalDetail.create();
        detail.setAction(action);
        detail.setAttachmentUrl(request.getImg());
        DeviceApplicationRequestDto.DeviceSelection primarySelection = primaryDeviceId != null
                ? perDeviceSelections.get(primaryDeviceId)
                : null;
        String requestedStatus = firstNonBlank(
                primarySelection != null ? primarySelection.getStatus() : null,
                request.getDeviceStatus(),
                request.getStatus());
        detail.setRequestedStatus(requestedStatus);
        detail.setRequestedPurpose(request.getDevicePurpose());
        detail.setRequestedUsageStartDate(request.getUsageStartDate());
        detail.setRequestedUsageEndDate(request.getUsageEndDate());
        detail.setMemo(request.getDescription());
        detail.replaceDevices(targetDevices, requestedOverrides);
        if (primaryDeviceId != null) {
            DeviceApprovalDetail.RequestedOverrides primaryOverride = requestedOverrides.get(primaryDeviceId);
            if (primaryOverride != null) {
                detail.setRequestedProject(primaryOverride.project());
                detail.setRequestedDepartment(primaryOverride.department());
                detail.setRequestedRealUser(primaryOverride.realUser());
            } else {
                detail.setRequestedProject(requestedProject);
                detail.setRequestedDepartment(requestedDepartment);
                detail.setRequestedRealUser(request.getRealUser());
            }
        } else {
            detail.setRequestedProject(requestedProject);
            detail.setRequestedDepartment(requestedDepartment);
            detail.setRequestedRealUser(request.getRealUser());
        }
        targetDevices.stream().findFirst().ifPresent(detail::updateFromDevice);

        ApprovalRequest approval = ApprovalRequest.builder()
                .category(ApprovalCategory.DEVICE)
                .status(ApprovalStatus.PENDING)
                .title(buildTitle(action, targetDevices))
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
        String nextStepMessage = null;
        if (nextStep != null) {
            nextStep.begin();
            approval.setStatus(ApprovalStatus.IN_PROGRESS);
            int nextSequence = nextStep.getSequence();
            nextStepMessage = (currentSequence > 0 ? currentSequence + "차 결재가 완료되어 " : "결재가 완료되어 ")
                    + (nextSequence > 0 ? nextSequence + "차 결재가 필요합니다." : "다음 결재가 필요합니다.");
        } else {
            approval.markApproved();
            applyDeviceStateOnCompletion(approval);
        }

        if (comment != null && !comment.isBlank()) {
            approvalCommentService.addComment(approvalId, normalizedApproverUsername, comment);
        }

        ApprovalRequest savedApproval = approvalRequestRepository.save(approval);

        if (nextStep != null) {
            notifyApplicantOnProgress(savedApproval, step);
            notifyApprover(savedApproval, nextStep, nextStepMessage);
        } else {
            notifyApplicantOnCompletion(savedApproval);
        }

        return new ApprovalDeviceDto(savedApproval);
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
            List<Devices> devices = detail.resolveDevices();
            if (!devices.isEmpty()) {
                for (Devices device : devices) {
                    if (device == null) {
                        continue;
                    }
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
                }
                devicesRepository.saveAll(devices);
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

    RealUserMode requestRealUserMode = updateRequest.getRealUserMode() == null
        ? null
        : RealUserMode.fromKey(updateRequest.getRealUserMode());

    String normalizedRealUser = null;
    if (requestRealUserMode == RealUserMode.MANUAL) {
        normalizedRealUser = Optional.ofNullable(updateRequest.getRealUser())
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .orElse(null);
    }

    String payloadUsername = Optional.ofNullable(updateRequest.getUsername())
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .orElse(null);
    String currentUsername = commonLookupService.currentUsernameFromJwt().orElse(null);
    String currentDisplayName = commonLookupService.currentUserDisplayNameFromJwt().orElse(null);
    String applicantAutoRealUser = firstNonBlank(
        currentDisplayName,
        currentUsername,
        payloadUsername,
        approval.getRequesterName());

    String departmentName = Optional.ofNullable(updateRequest.getDepartmentName())
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .orElse(null);
    Departments requestedDepartment = lookupDepartment(departmentName);
    if (departmentName != null && requestedDepartment == null) {
        throw new CustomException(CustomErrorCode.REQUESTED_DEPARTMENT_NOT_FOUND);
    }

    String projectIdentifier = firstNonBlank(updateRequest.getProjectName(), updateRequest.getProjectCode());
    Projects requestedProject = lookupProject(projectIdentifier);
    if (projectIdentifier != null && requestedProject == null) {
        throw new CustomException(CustomErrorCode.PROJECT_NOT_FOUND);
    }

    LinkedHashSet<String> deviceIds = detail.resolveDevices().stream()
        .map(Devices::getId)
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .collect(Collectors.toCollection(LinkedHashSet::new));

    DeviceApplicationRequestDto overrideSource = new DeviceApplicationRequestDto();
    overrideSource.setUserName(firstNonBlank(applicantAutoRealUser, approval.getRequesterName()));
    overrideSource.setRealUser(normalizedRealUser);
    overrideSource.setRealUserMode(requestRealUserMode != null ? requestRealUserMode.getKey() : null);
    overrideSource.setProjectName(updateRequest.getProjectName());
    overrideSource.setProjectCode(updateRequest.getProjectCode());
    overrideSource.setDepartmentName(updateRequest.getDepartmentName());
    overrideSource.setDevices(Optional.ofNullable(updateRequest.getDevices())
        .map(ArrayList::new)
        .orElseGet(ArrayList::new));

    Map<String, DeviceApplicationRequestDto.DeviceSelection> overrideSelections =
        toDeviceSelectionMap(overrideSource.getDevices());

    Map<String, List<String>> requestedTagsByDevice = new LinkedHashMap<>();
    overrideSelections.forEach((deviceId, selection) -> {
        List<String> sanitized = sanitizeTags(selection != null ? selection.getTags() : null);
        requestedTagsByDevice.put(deviceId, sanitized);
    });
    List<String> fallbackReturnTags = sanitizeTags(updateRequest.getTags());

    String defaultRealUser;
    if (requestRealUserMode == RealUserMode.MANUAL) {
        defaultRealUser = firstNonBlank(
            normalizedRealUser,
            detail.getRequestedRealUser(),
            approval.getRequesterName(),
            applicantAutoRealUser);
    } else {
        defaultRealUser = firstNonBlank(
            applicantAutoRealUser,
            detail.getRequestedRealUser(),
            approval.getRequesterName(),
            normalizedRealUser);
    }

    Map<String, DeviceApprovalDetail.RequestedOverrides> requestedOverrides = buildRequestedOverrides(
        deviceIds,
        overrideSource,
        requestedProject,
        requestedDepartment,
        defaultRealUser,
        overrideSelections);

    if (!requestedOverrides.isEmpty()) {
        detail.applyRequestedOverrides(requestedOverrides);
    } else {
        detail.setRequestedProject(requestedProject);
        detail.setRequestedDepartment(requestedDepartment);
        detail.setRequestedRealUser(normalizedRealUser);
    }

        if (updateRequest.getUsageStartDate() != null && updateRequest.getUsageEndDate() != null
                && updateRequest.getUsageEndDate().isBefore(updateRequest.getUsageStartDate())) {
            throw new CustomException(CustomErrorCode.APPROVAL_USAGE_END_BEFORE_START);
        }
        detail.setRequestedUsageStartDate(updateRequest.getUsageStartDate());
        detail.setRequestedUsageEndDate(updateRequest.getUsageEndDate());

    if (detail.getAction() == DeviceApprovalAction.RETURN) {
        detail.resolveDevices().stream()
            .filter(device -> device != null && device.getId() != null)
            .forEach(device -> {
            String deviceId = device.getId();
            List<String> specific = requestedTagsByDevice.containsKey(deviceId)
                ? requestedTagsByDevice.get(deviceId)
                : fallbackReturnTags;
            tagsService.replaceDeviceTags(device, specific);
            });
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

    private void applyDeviceStateOnSubmission(List<Devices> devices,
                                              DeviceApplicationRequestDto request,
                                              DeviceApprovalAction action,
                                              Map<String, DeviceApplicationRequestDto.DeviceSelection> selections,
                                              Map<String, DeviceApprovalDetail.RequestedOverrides> overrides) {
        if (devices == null || devices.isEmpty()) {
            return;
        }
        for (Devices device : devices) {
            DeviceApprovalDetail.RequestedOverrides override = null;
            if (device != null && overrides != null) {
                override = overrides.get(device.getId());
            }
            applyDeviceStateOnSubmission(device, request, action, selections, override);
        }
    }

    private void applyDeviceStateOnSubmission(Devices device,
                                              DeviceApplicationRequestDto request,
                                              DeviceApprovalAction action,
                                              Map<String, DeviceApplicationRequestDto.DeviceSelection> selections,
                                              DeviceApprovalDetail.RequestedOverrides override) {
        if (device == null) {
            return;
        }
        if (action != DeviceApprovalAction.RENTAL && request.getIsUsable() != null) {
            device.setIsUsable(request.getIsUsable());
        }

        if (action != DeviceApprovalAction.DISPOSAL) {
            DeviceApplicationRequestDto.DeviceSelection selection = selections != null
                    ? selections.get(device.getId())
                    : null;
            String perDeviceStatus = selection != null ? firstNonBlank(selection.getStatus()) : null;
            String resolvedStatus = firstNonBlank(perDeviceStatus, request.getStatus(), request.getDeviceStatus());
            if (resolvedStatus != null && !resolvedStatus.isBlank()) {
                device.setStatus(resolvedStatus.trim());
            }
        }

        devicesRepository.save(device);
    }

    private Map<String, DeviceApprovalDetail.RequestedOverrides> buildRequestedOverrides(
            LinkedHashSet<String> uniqueDeviceIds,
            DeviceApplicationRequestDto request,
            Projects defaultProject,
            Departments defaultDepartment,
            String defaultRealUser,
            Map<String, DeviceApplicationRequestDto.DeviceSelection> perDeviceSelections) {
        if (uniqueDeviceIds == null || uniqueDeviceIds.isEmpty()) {
            return Collections.emptyMap();
        }

    Map<String, DeviceApplicationRequestDto.DeviceSelection> selections = perDeviceSelections != null
        ? perDeviceSelections
        : toDeviceSelectionMap(request.getDevices());
    RealUserMode defaultMode = Optional.ofNullable(request.getRealUserMode())
        .map(RealUserMode::fromKey)
        .orElse(RealUserMode.AUTO);
    String normalizedDefaultRealUser = firstNonBlank(defaultRealUser);
    String requestLevelManualRealUser = defaultMode == RealUserMode.MANUAL
        ? firstNonBlank(request.getRealUser())
        : null;
        Map<String, DeviceApprovalDetail.RequestedOverrides> result = new LinkedHashMap<>();
        String defaultStatus = firstNonBlank(request.getStatus(), request.getDeviceStatus());

        for (String deviceId : uniqueDeviceIds) {
            DeviceApplicationRequestDto.DeviceSelection selection = selections.get(deviceId);

            Projects resolvedProject = resolveRequestedProject(selection, defaultProject);
            String requestedProjectName = firstNonBlank(
                    selection != null ? selection.getProjectName() : null,
                    Optional.ofNullable(resolvedProject).map(Projects::getName).orElse(null));
            String requestedProjectCode = firstNonBlank(
                    selection != null ? selection.getProjectCode() : null,
                    Optional.ofNullable(resolvedProject).map(Projects::getCode).orElse(null));

            Departments resolvedDepartment = resolveRequestedDepartment(selection, defaultDepartment);
            String requestedDepartmentName = firstNonBlank(
                    selection != null ? selection.getDepartmentName() : null,
                    Optional.ofNullable(resolvedDepartment).map(Departments::getName).orElse(null));

        RealUserMode mode = selection != null && selection.getRealUserMode() != null
            ? RealUserMode.fromKey(selection.getRealUserMode())
            : defaultMode;
        String manualRealUser = selection != null ? firstNonBlank(selection.getRealUser()) : null;
        if (manualRealUser == null && mode == RealUserMode.MANUAL) {
            manualRealUser = requestLevelManualRealUser;
        }
            String resolvedRealUser = mode == RealUserMode.MANUAL
                    ? firstNonBlank(manualRealUser, requestLevelManualRealUser)
            : normalizedDefaultRealUser;

            String selectionStatus = selection != null ? selection.getStatus() : null;
            String resolvedStatus = firstNonBlank(selectionStatus, defaultStatus);

            result.put(deviceId, new DeviceApprovalDetail.RequestedOverrides(
                    resolvedProject,
                    requestedProjectName,
                    requestedProjectCode,
                    resolvedDepartment,
                    requestedDepartmentName,
                    resolvedStatus,
                    resolvedRealUser,
                    mode));
        }
        return result;
    }

    private List<String> sanitizeTags(List<String> rawTags) {
        if (rawTags == null || rawTags.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String tag : rawTags) {
            if (tag == null) {
                continue;
            }
            String trimmed = tag.trim();
            if (!trimmed.isEmpty()) {
                unique.add(trimmed);
            }
        }
        return new ArrayList<>(unique);
    }

    private Map<String, DeviceApplicationRequestDto.DeviceSelection> toDeviceSelectionMap(
            List<DeviceApplicationRequestDto.DeviceSelection> selections) {
        if (selections == null || selections.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, DeviceApplicationRequestDto.DeviceSelection> map = new LinkedHashMap<>();
        for (DeviceApplicationRequestDto.DeviceSelection selection : selections) {
            if (selection == null) {
                continue;
            }
            String deviceId = firstNonBlank(selection.getDeviceId());
            if (deviceId == null) {
                continue;
            }
            map.putIfAbsent(deviceId, selection);
        }
        return map;
    }

    private Projects resolveRequestedProject(DeviceApplicationRequestDto.DeviceSelection selection,
                                             Projects defaultProject) {
        if (selection == null) {
            return defaultProject;
        }
        String identifier = firstNonBlank(selection.getProjectName(), selection.getProjectCode());
        if (identifier == null) {
            return defaultProject;
        }
        Projects project = lookupProject(identifier);
        if (project == null) {
            throw new CustomException(CustomErrorCode.PROJECT_NOT_FOUND);
        }
        return project;
    }

    private Departments resolveRequestedDepartment(DeviceApplicationRequestDto.DeviceSelection selection,
                                                   Departments defaultDepartment) {
        if (selection == null) {
            return defaultDepartment;
        }
        String name = firstNonBlank(selection.getDepartmentName());
        if (name == null) {
            return defaultDepartment;
        }
        Departments department = lookupDepartment(name);
        if (department == null) {
            throw new CustomException(CustomErrorCode.REQUESTED_DEPARTMENT_NOT_FOUND);
        }
        return department;
    }

    private void validateDuplicateDeviceAction(List<Devices> devices, DeviceApprovalAction requestedAction) {
        if (devices == null || devices.isEmpty()) {
            return;
        }
        for (Devices device : devices) {
            validateDuplicateDeviceAction(device, requestedAction);
        }
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

        DeviceApprovalAction action = detail.getAction();
        if (action == null) {
            return;
        }

        List<Devices> devices = detail.resolveDevices();
        if (devices.isEmpty()) {
            return;
        }

        boolean shouldResetMetadata = action == DeviceApprovalAction.RETURN
                || action == DeviceApprovalAction.DISPOSAL;
        Projects defaultProject = shouldResetMetadata ? resolveDefaultProjectMetadata() : null;
        Departments defaultDepartment = shouldResetMetadata ? resolveDefaultDepartmentMetadata() : null;

        for (Devices device : devices) {
            if (device == null) {
                continue;
            }
            DeviceApprovalItem detailItem = device.getId() != null ? detail.findItemByDeviceId(device.getId()) : null;
            switch (action) {
                case RENTAL -> applyRentalCompletion(device, approval, detail, detailItem);
                case RETURN -> applyReturnCompletion(device);
                case DISPOSAL -> applyDisposalCompletion(device);
                default -> {
                    // fall through
                }
            }
            applyRequestedAttributes(device, detail, detailItem, shouldResetMetadata, defaultProject, defaultDepartment);
        }
        devicesRepository.saveAll(devices);
    }

    private void applyRentalCompletion(Devices device,
                                       ApprovalRequest approval,
                                       DeviceApprovalDetail detail,
                                       DeviceApprovalItem detailItem) {
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

        String requestedRealUser = resolveRequestedRealUser(detail, detailItem);
        if (requestedRealUser == null || requestedRealUser.isBlank()) {
            String requesterName = Optional.ofNullable(approval.getRequesterName())
                    .map(String::trim)
                    .filter(name -> !name.isEmpty())
                    .orElse(null);
            if (requesterName != null) {
                device.setRealUser(requesterName);
            }
        }
    }

    private void applyReturnCompletion(Devices device) {
        device.setIsUsable(Boolean.TRUE);
        device.setUserUuid(null);
        device.setRealUser(null);
    }

    private void applyDisposalCompletion(Devices device) {
        device.setUserUuid(null);
        device.setRealUser(null);
    }

    private void applyRequestedAttributes(Devices device,
                                          DeviceApprovalDetail detail,
                                          DeviceApprovalItem detailItem,
                                          boolean resetToDefaultMetadata,
                                          Projects defaultProject,
                                          Departments defaultDepartment) {
        String requestedStatus = Optional.ofNullable(detailItem)
                .map(DeviceApprovalItem::getRequestedStatus)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElseGet(() -> Optional.ofNullable(detail.getRequestedStatus())
                        .map(String::trim)
                        .filter(value -> !value.isEmpty())
                        .orElse(null));
        if (requestedStatus != null) {
            device.setStatus(requestedStatus);
        }
        if (detail.getRequestedPurpose() != null && !detail.getRequestedPurpose().isBlank()) {
            device.setPurpose(detail.getRequestedPurpose());
        }
        if (resetToDefaultMetadata) {
            if (defaultProject != null) {
                device.setProjectId(defaultProject);
            } else {
                device.setProjectId(null);
            }
            if (defaultDepartment != null) {
                device.setManageDep(defaultDepartment);
            } else {
                device.setManageDep(null);
            }
        } else {
            Projects requestedProject = Optional.ofNullable(detailItem)
                    .map(DeviceApprovalItem::getRequestedProject)
                    .orElse(detail.getRequestedProject());
            if (requestedProject != null) {
                device.setProjectId(requestedProject);
            }
            Departments requestedDepartment = Optional.ofNullable(detailItem)
                    .map(DeviceApprovalItem::getRequestedDepartment)
                    .orElse(detail.getRequestedDepartment());
            if (requestedDepartment != null) {
                device.setManageDep(requestedDepartment);
            }
        }
        DeviceApprovalAction detailAction = detail != null ? detail.getAction() : null;
        if (detailAction != DeviceApprovalAction.RETURN && detailAction != DeviceApprovalAction.DISPOSAL) {
            String realUser = resolveRequestedRealUser(detail, detailItem);
            if (realUser != null && !realUser.isBlank()) {
                device.setRealUser(realUser);
            }
        }
    }

    private String resolveRequestedRealUser(DeviceApprovalDetail detail, DeviceApprovalItem detailItem) {
        String perDevice = Optional.ofNullable(detailItem)
                .map(DeviceApprovalItem::getRequestedRealUser)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse(null);
        if (perDevice != null) {
            return perDevice;
        }
        return Optional.ofNullable(detail)
                .map(DeviceApprovalDetail::getRequestedRealUser)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .orElse(null);
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
        if (detail != null) {
            List<String> deviceIds = detail.resolveDevices().stream()
                    .map(Devices::getId)
                    .filter(id -> id != null && !id.isBlank())
                    .toList();
            if (!deviceIds.isEmpty()) {
                if (deviceIds.size() == 1) {
                    return deviceIds.get(0);
                }
                return deviceIds.get(0) + " 외 " + (deviceIds.size() - 1) + "대";
            }
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

    private String buildTitle(DeviceApprovalAction action, List<Devices> devices) {
        if (action == null) {
            return "";
        }
        if (devices == null || devices.isEmpty()) {
            return action.getDisplayName();
        }

        List<String> deviceIds = devices.stream()
                .map(device -> device != null ? device.getId() : null)
                .filter(id -> id != null && !id.isBlank())
                .toList();

        if (deviceIds.isEmpty()) {
            return action.getDisplayName();
        }
        if (deviceIds.size() == 1) {
            return action.getDisplayName() + " - " + deviceIds.get(0);
        }
        return action.getDisplayName() + " - " + deviceIds.get(0) + " 외 " + (deviceIds.size() - 1) + "대";
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

    private Projects resolveDefaultProjectMetadata() {
        return lookupProject(DEFAULT_METADATA_PROJECT_NAME);
    }

    private Departments resolveDefaultDepartmentMetadata() {
        return lookupDepartment(DEFAULT_METADATA_DEPARTMENT_NAME);
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
