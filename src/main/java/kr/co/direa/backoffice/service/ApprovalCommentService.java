package kr.co.direa.backoffice.service;

import kr.co.direa.backoffice.constant.Constants;
import kr.co.direa.backoffice.domain.ApprovalComment;
import kr.co.direa.backoffice.domain.Approvals;
import kr.co.direa.backoffice.domain.Users;
import kr.co.direa.backoffice.dto.ApprovalCommentDto;
import kr.co.direa.backoffice.repository.ApprovalCommentRepository;
import kr.co.direa.backoffice.repository.ApprovalDevicesRepository;
import kr.co.direa.backoffice.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ApprovalCommentService {
	private final ApprovalCommentRepository approvalCommentRepository;
	private final ApprovalDevicesRepository approvalDevicesRepository;
	private final UsersRepository usersRepository;
	private final NotificationService notificationService;
	// private final MailService mailService; // Mail sending disabled during local development

	@Transactional
	public ApprovalCommentDto addComment(Long approvalId, String username, String content) {
		if (content == null || content.isBlank()) {
			throw new IllegalArgumentException("Comment content must not be empty");
		}
		Approvals approvals = approvalDevicesRepository.findById(approvalId)
				.orElseThrow(() -> new IllegalArgumentException("Approval not found: " + approvalId));
		Users user = usersRepository.findByUsername(username)
				.orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

		ApprovalComment comment = ApprovalComment.builder()
				.approvals(approvals)
				.user(user)
				.content(content)
				.build();
	ApprovalComment saved = approvalCommentRepository.save(comment);
	notifyParticipants(approvals, user, content);
	return new ApprovalCommentDto(saved);
	}

	@Transactional(readOnly = true)
	public List<ApprovalCommentDto> findByApproval(Long approvalId) {
		return approvalCommentRepository.findByApprovalsIdOrderByCreatedDateAsc(approvalId).stream()
				.map(ApprovalCommentDto::new)
				.toList();
	}

	private void notifyParticipants(Approvals approvals, Users author, String content) {
		if (!(approvals instanceof kr.co.direa.backoffice.domain.ApprovalDevices approvalDevices)) {
			return;
		}

		String subject = "[장비 결재 댓글] " + buildApprovalSubject(approvalDevices);
		String link = "/approvals/" + approvalDevices.getId();

		java.util.Set<String> receivers = new java.util.HashSet<>();
		if (approvalDevices.getUserId() != null && approvalDevices.getUserId().getEmail() != null) {
			receivers.add(approvalDevices.getUserId().getEmail());
		}
	if (approvalDevices.getApprovers() != null) {
	    approvalDevices.getApprovers().stream()
		    .map(approver -> approver.getUsers().getEmail())
		    .filter(email -> email != null && !email.isBlank())
		    .forEach(receivers::add);
	}

		if (author.getEmail() != null) {
			receivers.remove(author.getEmail());
		}

		for (String receiver : receivers) {
			// String body = author.getUsername() + "님의 댓글: " + System.lineSeparator() + content;
			// mailService.sendMail(receiver, subject, body);
			notificationService.createNotification(receiver, subject, Constants.COMMENT_TYPE, link);
		}
	}

	private String buildApprovalSubject(kr.co.direa.backoffice.domain.ApprovalDevices approval) {
		String deviceId = approval.getDeviceId() != null ? approval.getDeviceId().getId() : "";
		return deviceId.isBlank() ? "승인 ID " + approval.getId() : deviceId;
	}
}
