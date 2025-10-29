package kr.co.direa.backoffice.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.co.direa.backoffice.constant.Constants;
import kr.co.direa.backoffice.domain.ApprovalRequest;
import kr.co.direa.backoffice.domain.ApprovalStep;
import kr.co.direa.backoffice.domain.Departments;
import kr.co.direa.backoffice.domain.DeviceApprovalDetail;
import kr.co.direa.backoffice.domain.Devices;
import kr.co.direa.backoffice.domain.Projects;
import kr.co.direa.backoffice.domain.Users;
import kr.co.direa.backoffice.domain.enums.ApprovalCategory;
import kr.co.direa.backoffice.domain.enums.ApprovalStatus;
import kr.co.direa.backoffice.domain.enums.DeviceApprovalAction;
import kr.co.direa.backoffice.domain.enums.StepStatus;
import kr.co.direa.backoffice.dto.ApprovalDeviceDto;
import kr.co.direa.backoffice.dto.DeviceApplicationRequestDto;
import kr.co.direa.backoffice.repository.ApprovalRequestRepository;
import kr.co.direa.backoffice.repository.ApprovalStepRepository;
import kr.co.direa.backoffice.repository.DepartmentsRepository;
import kr.co.direa.backoffice.repository.DevicesRepository;
import kr.co.direa.backoffice.repository.ProjectsRepository;
import kr.co.direa.backoffice.repository.UsersRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ApprovalDeviceService {
    private final ApprovalRequestRepository approvalRequestRepository;
    private final ApprovalStepRepository approvalStepRepository;
    private final DevicesRepository devicesRepository;
    private final UsersRepository usersRepository;
    private final ProjectsRepository projectsRepository;
    private final DepartmentsRepository departmentsRepository;
    private final NotificationService notificationService;
    private final ApprovalCommentService approvalCommentService;
    private final CommonLookupService commonLookupService;

    @Transactional
    public ApprovalDeviceDto submitApplication(DeviceApplicationRequestDto request) {
        DeviceApprovalAction action = Optional.ofNullable(DeviceApprovalAction.fromDisplayName(request.getType()))
                .orElseThrow(() -> new IllegalArgumentException("Unsupported approval type: " + request.getType()));

        Devices device = devicesRepository.findById(request.getDeviceId())
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + request.getDeviceId()));
        Users requester = usersRepository.findByUsername(request.getUserName()).orElse(null);
        UUID requesterExternalId = commonLookupService.resolveKeycloakUserIdByUsername(request.getUserName())
                .map(this::safeUuid)
                .orElse(null);

        Projects requestedProject = lookupProject(request.getProjectName());
        Departments requestedDepartment = lookupDepartment(request.getDepartmentName());

        applyDeviceStateOnSubmission(device, request, action, requester);

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
                .requester(requester)
                .category(ApprovalCategory.DEVICE)
                .status(ApprovalStatus.PENDING)
                .title(buildTitle(action, device))
                .reason(request.getReason())
                .dueDate(request.getDeadline())
                .build();

        if (requester == null) {
            approval.setRequesterName(request.getUserName());
        }
        if (approval.getRequesterEmail() == null && requester != null) {
            approval.setRequesterEmail(requester.getEmail());
        }
        if (requesterExternalId != null) {
            approval.setRequesterExternalId(requesterExternalId);
        }

        detail.attachTo(approval);
        buildApprovalSteps(approval, request.getApprovers());
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
        Users approverUser = usersRepository.findByUsername(approverUsername).orElse(null);
        UUID approverExternalId = commonLookupService.resolveKeycloakUserIdByUsername(approverUsername)
                .map(this::safeUuid)
                .orElse(null);

        ApprovalStep step = resolveApprovalStep(approvalId, approverUsername, approverUser, approverExternalId)
                .orElseThrow(() -> new IllegalStateException("Approver not assigned to this approval"));

        applyApproverMetadata(step, approverUser, approverUsername, approverExternalId);

        if (step.getStatus().isDecisionMade()) {
            throw new IllegalStateException("Approval already processed for this step");
        }

        ensurePreviousStepsApproved(approval, step.getSequence());

        step.markApproved(comment);

        ApprovalStep nextStep = findNextStep(approval, step.getSequence());
        if (nextStep != null) {
            nextStep.begin();
            approval.setStatus(ApprovalStatus.IN_PROGRESS);
            notifyApprover(approval, nextStep, "1차 결재가 완료되어 2차 결재가 필요합니다.");
        } else {
            approval.markApproved();
            notifyApplicantOnCompletion(approval);
        }

        if (comment != null && !comment.isBlank()) {
            approvalCommentService.addComment(approvalId, approverUsername, comment);
        }

        return new ApprovalDeviceDto(approvalRequestRepository.save(approval));
    }

    @Transactional
    public ApprovalDeviceDto reject(Long approvalId, String approverUsername, String reason) {
        ApprovalRequest approval = approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval not found: " + approvalId));
        Users approverUser = usersRepository.findByUsername(approverUsername).orElse(null);
        UUID approverExternalId = commonLookupService.resolveKeycloakUserIdByUsername(approverUsername)
                .map(this::safeUuid)
                .orElse(null);

        ApprovalStep step = resolveApprovalStep(approvalId, approverUsername, approverUser, approverExternalId)
                .orElseThrow(() -> new IllegalStateException("Approver not assigned to this approval"));

        applyApproverMetadata(step, approverUser, approverUsername, approverExternalId);

        if (step.getStatus().isDecisionMade()) {
            throw new IllegalStateException("Approval already processed for this step");
        }

        step.markRejected(reason);
        approval.markRejected();

        if (reason != null && !reason.isBlank()) {
            approvalCommentService.addComment(approvalId, approverUsername, reason);
        }

        notifyApplicantOnRejection(approval, approverUser, reason);

        return new ApprovalDeviceDto(approvalRequestRepository.save(approval));
    }

    @Transactional
    public ApprovalDeviceDto updateApprovers(Long approvalId, List<String> approverUsernames) {
        ApprovalRequest approval = approvalRequestRepository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval not found: " + approvalId));

        if (approverUsernames == null || approverUsernames.isEmpty()) {
            throw new IllegalArgumentException("Approver list must not be empty");
        }

        approval.getSteps().clear();
        buildApprovalSteps(approval, approverUsernames);
        approval.restartWorkflow();

        ApprovalRequest saved = approvalRequestRepository.save(approval);
        saved.getSteps().stream()
                .filter(step -> step.getStatus() == StepStatus.IN_PROGRESS)
                .findFirst()
                .ifPresent(step -> notifyApprover(saved, step, "장비 결재 요청 승인자가 변경되었습니다."));

        return new ApprovalDeviceDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ApprovalDeviceDto> findPendingApprovals() {
        List<ApprovalRequest> approvals = approvalRequestRepository.findByCategoryAndStatusIn(
                ApprovalCategory.DEVICE,
                List.of(ApprovalStatus.PENDING, ApprovalStatus.IN_PROGRESS));
        return approvals.stream().map(ApprovalDeviceDto::new).toList();
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
            Users approver = usersRepository.findByUsername(username).orElse(null);
            UUID externalId = commonLookupService.resolveKeycloakUserIdByUsername(username)
                    .map(this::safeUuid)
                    .orElse(null);

            ApprovalStep step = ApprovalStep.builder()
                    .approver(approver)
                    .sequence(sequence++)
                    .build();
            applyApproverMetadata(step, approver, username, externalId);
            approval.addStep(step);
        }
    }

    private void applyDeviceStateOnSubmission(Devices device, DeviceApplicationRequestDto request,
                                              DeviceApprovalAction action, Users requester) {
        if (action == DeviceApprovalAction.RENTAL) {
            device.setIsUsable(Boolean.FALSE);
        } else if (request.getIsUsable() != null) {
            device.setIsUsable(request.getIsUsable());
        }

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            device.setStatus(request.getStatus());
        }

    String fallbackUsername = requester != null ? requester.getUsername() : request.getUserName();
    String realUser = Optional.ofNullable(request.getRealUser())
        .filter(name -> !name.isBlank())
        .orElse(fallbackUsername);
        device.setRealUser(realUser);

        devicesRepository.save(device);
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
    String targetEmail = Optional.ofNullable(step.getApprover())
        .map(Users::getEmail)
        .orElse(step.getApproverEmail());
    if (targetEmail == null || targetEmail.isBlank()) {
            return;
        }
    String subjectPrefix = (message != null && !message.isBlank()) ? message : "[장비 결재 요청]";
    String subject = subjectPrefix + " " + buildApprovalSubject(approval);
    notificationService.createNotification(targetEmail, subject,
                Constants.NOTIFICATION_APPROVAL, buildApprovalLink(approval.getId()));
    }

    private void notifyApplicantOnCompletion(ApprovalRequest approval) {
    String targetEmail = Optional.ofNullable(approval.getRequester())
        .map(Users::getEmail)
        .orElse(approval.getRequesterEmail());
    if (targetEmail == null || targetEmail.isBlank()) {
            return;
        }
        String subject = "[장비 결재 완료] " + buildApprovalSubject(approval);
    notificationService.createNotification(targetEmail, subject,
                Constants.NOTIFICATION_APPROVAL, buildApprovalLink(approval.getId()));
    }

    private void notifyApplicantOnRejection(ApprovalRequest approval, Users approver, String reason) {
        String subject = "[장비 결재 반려] " + buildApprovalSubject(approval);

    String requesterEmail = Optional.ofNullable(approval.getRequester())
        .map(Users::getEmail)
        .orElse(approval.getRequesterEmail());
    if (requesterEmail != null && !requesterEmail.isBlank()) {
        notificationService.createNotification(requesterEmail, subject,
                    Constants.NOTIFICATION_APPROVAL, buildApprovalLink(approval.getId()));
        }

    String approverEmail = Optional.ofNullable(approver)
        .map(Users::getEmail)
        .orElse(null);
    if (approverEmail != null && !approverEmail.isBlank()) {
        notificationService.createNotification(approverEmail, subject,
                    Constants.NOTIFICATION_APPROVAL, buildApprovalLink(approval.getId()));
        }
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
        return "/approvals/" + approvalId;
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
                                                       Users approverUser,
                                                       UUID approverExternalId) {
        Optional<ApprovalStep> step = Optional.empty();
        if (approverUser != null) {
            step = approvalStepRepository.findByRequestIdAndApproverId(approvalId, approverUser.getId());
        }
        if (step.isEmpty() && approverExternalId != null) {
            step = approvalStepRepository.findByRequestIdAndApproverExternalId(approvalId, approverExternalId);
        }
        if (step.isEmpty()) {
            step = approvalStepRepository.findByRequestIdAndApproverName(approvalId, approverUsername);
        }
        return step;
    }

    private void applyApproverMetadata(ApprovalStep step,
                                       Users approverUser,
                                       String approverUsername,
                                       UUID approverExternalId) {
        if (step == null) {
            return;
        }
        if (step.getApprover() == null && approverUser != null) {
            step.setApprover(approverUser);
        }
        if (step.getApproverName() == null || step.getApproverName().isBlank()) {
            step.setApproverName(approverUser != null ? approverUser.getUsername() : approverUsername);
        }
        if (step.getApproverEmail() == null && approverUser != null) {
            step.setApproverEmail(approverUser.getEmail());
        }
        if (approverExternalId != null && step.getApproverExternalId() == null) {
            step.setApproverExternalId(approverExternalId);
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
}
