package kr.co.direa.backoffice.service;

import kr.co.direa.backoffice.constant.Constants;
import kr.co.direa.backoffice.domain.ApprovalDevices;
import kr.co.direa.backoffice.domain.Approver;
import kr.co.direa.backoffice.domain.Departments;
import kr.co.direa.backoffice.domain.Devices;
import kr.co.direa.backoffice.domain.Projects;
import kr.co.direa.backoffice.domain.Users;
import kr.co.direa.backoffice.dto.ApprovalDeviceDto;
import kr.co.direa.backoffice.dto.DeviceApplicationRequestDto;
import kr.co.direa.backoffice.repository.ApprovalDevicesRepository;
import kr.co.direa.backoffice.repository.ApproverRepository;
import kr.co.direa.backoffice.repository.DepartmentsRepository;
import kr.co.direa.backoffice.repository.DevicesRepository;
import kr.co.direa.backoffice.repository.ProjectsRepository;
import kr.co.direa.backoffice.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApprovalDeviceService {
    private final ApprovalDevicesRepository approvalDevicesRepository;
    private final ApproverRepository approverRepository;
    private final DevicesRepository devicesRepository;
    private final UsersRepository usersRepository;
    private final ProjectsRepository projectsRepository;
    private final DepartmentsRepository departmentsRepository;
    private final NotificationService notificationService;
    // private final MailService mailService; // Mail sending disabled for local development
    private final ApprovalCommentService approvalCommentService;

    @Transactional
    public ApprovalDeviceDto submitApplication(DeviceApplicationRequestDto request) {
        Devices device = devicesRepository.findById(request.getDeviceId())
                .orElseThrow(() -> new IllegalArgumentException("Device not found: " + request.getDeviceId()));
        Users user = usersRepository.findByUsername(request.getUserName())
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.getUserName()));
        Projects project = request.getProjectName() != null ? projectsRepository.findByName(request.getProjectName()) : null;
        if (project == null && request.getProjectName() != null) {
            project = projectsRepository.findByCode(request.getProjectName()).orElse(null);
        }
        Departments department = request.getDepartmentName() != null
                ? departmentsRepository.findByName(request.getDepartmentName()).orElse(null)
                : null;

        if (Constants.APPROVAL_RENTAL.equals(request.getType())) {
            device.setIsUsable(Boolean.FALSE);
        } else {
            device.setIsUsable(request.getIsUsable() != null ? request.getIsUsable() : device.getIsUsable());
        }
        device.setStatus(request.getStatus() != null ? request.getStatus() : device.getStatus());
        device.setRealUser(request.getRealUser() != null ? request.getRealUser() : user.getUsername());
        devicesRepository.save(device);

        ApprovalDevices approval = new ApprovalDevices();
        approval.setUserId(user);
        approval.setDeviceId(device);
        approval.setReason(request.getReason());
        approval.setApprovalInfo(Constants.APPROVAL_WAITING);
        approval.setType(request.getType());
        approval.setDeadline(request.getDeadline());
        approval.setTmpProject(project);
        approval.setTmpDepartment(department);

        approval = approvalDevicesRepository.save(approval);

        List<Approver> approvers = new ArrayList<>();
        List<String> approverNames = request.getApprovers();
        if (approverNames != null && !approverNames.isEmpty()) {
            if (approverNames.size() != 2) {
                throw new IllegalArgumentException("Exactly two approvers must be provided for a device application");
            }
            for (int i = 0; i < approverNames.size(); i++) {
                String approverUsername = approverNames.get(i);
                Users approverUser = usersRepository.findByUsername(approverUsername)
                        .orElseThrow(() -> new IllegalArgumentException("Approver not found: " + approverUsername));
                approvers.add(Approver.builder()
                        .users(approverUser)
                        .approvals(approval)
                        .isApproved(Boolean.FALSE)
                        .step(i + 1)
                        .build());
            }
            approverRepository.saveAll(approvers);
            approval.setApprovers(approvers);
            if (!approvers.isEmpty()) {
                notifyApprover(approval, approvers.get(0), "새 장비 결재 요청이 도착했습니다.");
            }
        }

        return new ApprovalDeviceDto(approval);
    }

    @Transactional
    public ApprovalDeviceDto approve(Long approvalId, String approverUsername, String comment) {
        ApprovalDevices approval = approvalDevicesRepository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval not found: " + approvalId));
        Users approverUser = usersRepository.findByUsername(approverUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + approverUsername));

        Approver approver = approverRepository.findByApprovalsIdAndUsersId(approvalId, approverUser.getId())
                .orElseThrow(() -> new IllegalStateException("Approver not assigned to this approval"));

        if (Boolean.TRUE.equals(approver.getIsApproved())) {
            throw new IllegalStateException("Approval already processed for this step");
        }

        ensurePreviousStepsApproved(approval, approver.getStep());

        approver.setIsApproved(Boolean.TRUE);

        Approver nextApprover = findNextApprover(approval, approver.getStep());
        if (nextApprover != null) {
            approval.setApprovalInfo(Constants.APPROVAL_STEP1_COMPLETED);
            notifyApprover(approval, nextApprover, "1차 결재가 완료되어 2차 결재가 필요합니다.");
        } else {
            approval.setApprovalInfo(Constants.APPROVAL_COMPLETED);
            notifyApplicantOnCompletion(approval);
        }

        if (comment != null && !comment.isBlank()) {
            approvalCommentService.addComment(approvalId, approverUsername, comment);
        }

        return new ApprovalDeviceDto(approvalDevicesRepository.save(approval));
    }

    @Transactional
    public ApprovalDeviceDto reject(Long approvalId, String approverUsername, String reason) {
        ApprovalDevices approval = approvalDevicesRepository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval not found: " + approvalId));
        Users approverUser = usersRepository.findByUsername(approverUsername)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + approverUsername));

        Approver approver = approverRepository.findByApprovalsIdAndUsersId(approvalId, approverUser.getId())
                .orElseThrow(() -> new IllegalStateException("Approver not assigned to this approval"));

        if (Boolean.TRUE.equals(approver.getIsApproved())) {
            throw new IllegalStateException("Approval was already completed");
        }

        approval.setApprovalInfo(Constants.APPROVAL_REJECT);
        approval.setReason(reason != null ? reason : approval.getReason());

        if (reason != null && !reason.isBlank()) {
            approvalCommentService.addComment(approvalId, approverUsername, reason);
        }

    notifyApplicantOnRejection(approval, approverUser, reason);

        return new ApprovalDeviceDto(approvalDevicesRepository.save(approval));
    }

    @Transactional
    public ApprovalDeviceDto updateApprovers(Long approvalId, List<String> approverUsernames) {
        ApprovalDevices approval = approvalDevicesRepository.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval not found: " + approvalId));

        if (approverUsernames == null || approverUsernames.size() != 2) {
            throw new IllegalArgumentException("Approver list must contain exactly two users");
        }

        List<Approver> existing = approverRepository.findByApprovalsId(approvalId);
        if (!existing.isEmpty()) {
            approverRepository.deleteAll(existing);
        }

        List<Approver> newApprovers = new ArrayList<>();
        for (int i = 0; i < approverUsernames.size(); i++) {
            String username = approverUsernames.get(i);
            Users approverUser = usersRepository.findByUsername(username)
                    .orElseThrow(() -> new IllegalArgumentException("Approver not found: " + username));
            newApprovers.add(Approver.builder()
                    .approvals(approval)
                    .users(approverUser)
                    .isApproved(Boolean.FALSE)
                    .step(i + 1)
                    .build());
        }
        if (!newApprovers.isEmpty()) {
            approverRepository.saveAll(newApprovers);
            approval.setApprovers(newApprovers);
            approval.setApprovalInfo(Constants.APPROVAL_WAITING);
            notifyApprover(approval, newApprovers.get(0), "장비 결재 요청 승인자가 변경되었습니다.");
        }

        return new ApprovalDeviceDto(approvalDevicesRepository.save(approval));
    }

    @Transactional(readOnly = true)
    public List<ApprovalDeviceDto> findPendingApprovals() {
        return approvalDevicesRepository.findAll().stream()
                .filter(approval -> !Constants.APPROVAL_COMPLETED.equals(approval.getApprovalInfo())
                        && !Constants.APPROVAL_REJECT.equals(approval.getApprovalInfo()))
                .map(ApprovalDeviceDto::new)
                .toList();
    }

    @Transactional(readOnly = true)
    public ApprovalDeviceDto findDetail(Long approvalId) {
    ApprovalDevices approval = approvalDevicesRepository.findById(approvalId)
        .orElseThrow(() -> new IllegalArgumentException("Approval not found: " + approvalId));
    return new ApprovalDeviceDto(approval);
    }

    private void ensurePreviousStepsApproved(ApprovalDevices approval, int currentStep) {
        if (currentStep <= 1) {
            return;
        }
        boolean previousApproved = approval.getApprovers().stream()
                .filter(a -> a.getStep() < currentStep)
                .allMatch(a -> Boolean.TRUE.equals(a.getIsApproved()));
        if (!previousApproved) {
            throw new IllegalStateException("Previous approval steps are not completed");
        }
    }

    private Approver findNextApprover(ApprovalDevices approval, int currentStep) {
        return approval.getApprovers().stream()
                .filter(a -> a.getStep() > currentStep)
                .sorted((a, b) -> Integer.compare(a.getStep(), b.getStep()))
                .findFirst()
                .orElse(null);
    }

    private void notifyApprover(ApprovalDevices approval, Approver approver, String message) {
        Users approverUser = approver.getUsers();
        String email = approverUser.getEmail();
    String subject = "[장비 결재 요청] " + buildApprovalSubject(approval);
    // String body = message + System.lineSeparator() + "링크: " + buildApprovalLink(approval.getId());
    // mailService.sendMail(email, subject, body);
        notificationService.createNotification(email, subject, Constants.NOTIFICATION_APPROVAL, buildApprovalLink(approval.getId()));
    }

    private void notifyApplicantOnCompletion(ApprovalDevices approval) {
        Users applicant = approval.getUserId();
        if (applicant == null) {
            return;
        }
    String subject = "[장비 결재 완료] " + buildApprovalSubject(approval);
    // String body = "신청하신 장비 결재가 최종 승인되었습니다.";
    // mailService.sendMail(applicant.getEmail(), subject, body);
        notificationService.createNotification(applicant.getEmail(), subject, Constants.APPROVAL_RENTAL, buildApprovalLink(approval.getId()));
    }

    private void notifyApplicantOnRejection(ApprovalDevices approval, Users approverUser, String reason) {
        Users applicant = approval.getUserId();
        if (applicant == null) {
            return;
        }
        String subject = "[장비 결재 반려] " + buildApprovalSubject(approval);
        // StringBuilder body = new StringBuilder("결재가 반려되었습니다.");
        // if (reason != null && !reason.isBlank()) {
        //     body.append(System.lineSeparator()).append("사유: ").append(reason);
        // }
        // mailService.sendMail(applicant.getEmail(), subject, body.toString());
        notificationService.createNotification(applicant.getEmail(), subject, Constants.APPROVAL_RENTAL, buildApprovalLink(approval.getId()));
        if (approverUser != null && approverUser.getEmail() != null) {
            notificationService.createNotification(approverUser.getEmail(), subject, Constants.APPROVAL_RENTAL, buildApprovalLink(approval.getId()));
        }
    }

    private String buildApprovalSubject(ApprovalDevices approval) {
        String deviceId = approval.getDeviceId() != null ? approval.getDeviceId().getId() : "";
        return deviceId.isBlank() ? "승인 ID " + approval.getId() : deviceId;
    }

    private String buildApprovalLink(Long approvalId) {
        return "/approvals/" + approvalId;
    }
}
