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
import kr.co.direa.backoffice.repository.ApprovalRequestRepository;
import kr.co.direa.backoffice.repository.ApprovalStepRepository;
import kr.co.direa.backoffice.repository.DepartmentsRepository;
import kr.co.direa.backoffice.repository.DevicesRepository;
import kr.co.direa.backoffice.repository.ProjectsRepository;
import kr.co.direa.backoffice.vo.ApprovalSearchRequest;
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

    @Transactional
    public ApprovalDeviceDto submitApplication(DeviceApplicationRequestDto request) {
    DeviceApprovalAction action = Optional.ofNullable(DeviceApprovalAction.fromDisplayName(request.getType()))
        .orElseThrow(() -> new IllegalArgumentException("Unsupported approval type: " + request.getType()));

    Devices device = devicesRepository.findById(request.getDeviceId())
        .orElseThrow(() -> new IllegalArgumentException("Device not found: " + request.getDeviceId()));
    CommonLookupService.KeycloakUserInfo requesterInfo = resolveUserInfo(request.getUserName());
    List<String> approverUsernames = prepareApproverSequence(request.getApprovers(), true);
    if (approverUsernames.isEmpty()) {
        throw new IllegalStateException("No approvers available for device approval workflow");
    }

        Projects requestedProject = lookupProject(request.getProjectName());
        Departments requestedDepartment = lookupDepartment(request.getDepartmentName());

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
        .orElseThrow(() -> new IllegalArgumentException("Approval not found: " + approvalId));
    CommonLookupService.KeycloakUserInfo approverInfo = resolveUserInfo(approverUsername);
    String normalizedApproverUsername = safeUsername(approverInfo, approverUsername);
    if (normalizedApproverUsername == null) {
        throw new IllegalArgumentException("Approver username must not be empty");
    }
    UUID approverExternalId = approverInfo != null ? approverInfo.id() : null;

    ApprovalStep step = resolveApprovalStep(approvalId, normalizedApproverUsername, approverExternalId)
                .orElseThrow(() -> new IllegalStateException("Approver not assigned to this approval"));

    applyApproverMetadata(step, approverInfo, normalizedApproverUsername);

        if (step.getStatus().isDecisionMade()) {
            throw new IllegalStateException("Approval already processed for this step");
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
                .orElseThrow(() -> new IllegalArgumentException("Approval not found: " + approvalId));
        CommonLookupService.KeycloakUserInfo approverInfo = resolveUserInfo(approverUsername);
        String normalizedApproverUsername = safeUsername(approverInfo, approverUsername);
        if (normalizedApproverUsername == null) {
            throw new IllegalArgumentException("Approver username must not be empty");
        }
        UUID approverExternalId = approverInfo != null ? approverInfo.id() : null;

        ApprovalStep step = resolveApprovalStep(approvalId, normalizedApproverUsername, approverExternalId)
                .orElseThrow(() -> new IllegalStateException("Approver not assigned to this approval"));

        applyApproverMetadata(step, approverInfo, normalizedApproverUsername);

        if (step.getStatus().isDecisionMade()) {
            throw new IllegalStateException("Approval already processed for this step");
        }

        step.markRejected(reason);
        approval.markRejected();

        if (reason != null && !reason.isBlank()) {
            approvalCommentService.addComment(approvalId, normalizedApproverUsername, reason);
        }

    notifyApplicantOnRejection(approval, approverInfo, normalizedApproverUsername);

        return new ApprovalDeviceDto(approvalRequestRepository.save(approval));
    }

    @Transactional
    public ApprovalDeviceDto updateApprovers(Long approvalId, List<String> approverUsernames) {
        ApprovalRequest approval = approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval not found: " + approvalId));

        List<String> sanitizedApprovers = prepareApproverSequence(approverUsernames, false);
        if (sanitizedApprovers.isEmpty()) {
            throw new IllegalArgumentException("Approver list must not be empty");
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

    @Transactional(readOnly = true)
    public ApprovalDeviceDto findDetail(Long approvalId) {
        ApprovalRequest approval = approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval not found: " + approvalId));
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
                throw new IllegalArgumentException("Approver username must not be empty");
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
        if (action == DeviceApprovalAction.RENTAL) {
            device.setIsUsable(Boolean.FALSE);
        } else if (request.getIsUsable() != null) {
            device.setIsUsable(request.getIsUsable());
        }

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
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
        if (detail.getRequestedRealUser() != null && !detail.getRequestedRealUser().isBlank()) {
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
            throw new IllegalStateException("Previous approval steps are not completed");
        }
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
        notificationService.createNotification(targetReceiver, subject,
                Constants.NOTIFICATION_APPROVAL, buildApprovalLink(approval.getId()));
    }

    private void notifyApplicantOnCompletion(ApprovalRequest approval) {
    String targetReceiver = Optional.ofNullable(approval.getRequesterName())
        .filter(name -> !name.isBlank())
        .orElse(null);
        if (targetReceiver == null || targetReceiver.isBlank()) {
            return;
        }
        String subject = "[장비 결재 완료] " + buildApprovalSubject(approval);
        notificationService.createNotification(targetReceiver, subject,
                Constants.NOTIFICATION_APPROVAL, buildApprovalLink(approval.getId()));
    }

    private void notifyApplicantOnRejection(ApprovalRequest approval,
                                            CommonLookupService.KeycloakUserInfo approverInfo,
                                            String approverUsername) {
        String subject = "[장비 결재 반려] " + buildApprovalSubject(approval);

    String requesterReceiver = Optional.ofNullable(approval.getRequesterName())
        .filter(name -> !name.isBlank())
        .orElse(null);
        if (requesterReceiver != null && !requesterReceiver.isBlank()) {
            notificationService.createNotification(requesterReceiver, subject,
                    Constants.NOTIFICATION_APPROVAL, buildApprovalLink(approval.getId()));
        }

    String approverReceiver = Optional.ofNullable(safeUsername(approverInfo, approverUsername))
        .orElse(approverUsername);
        if (approverReceiver != null && !approverReceiver.isBlank()) {
            notificationService.createNotification(approverReceiver, subject,
                    Constants.NOTIFICATION_APPROVAL, buildApprovalLink(approval.getId()));
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
                Constants.NOTIFICATION_APPROVAL, buildApprovalLink(approval.getId()));
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

    private String buildApprovalLink(Long approvalId) {
        return "/admin/approvals/" + approvalId;
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
            return new CommonLookupService.KeycloakUserInfo(stageUuid, resolvedUsername, null, displayName);
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
            throw new IllegalStateException("요청자 Keycloak UUID를 설정할 수 없습니다.");
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
