package kr.co.direa.backoffice.service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import kr.co.direa.backoffice.constant.Constants;
import kr.co.direa.backoffice.domain.ApprovalComment;
import kr.co.direa.backoffice.domain.ApprovalRequest;
import kr.co.direa.backoffice.domain.ApprovalStep;
import kr.co.direa.backoffice.domain.DeviceApprovalDetail;
import kr.co.direa.backoffice.domain.Devices;
import kr.co.direa.backoffice.domain.Users;
import kr.co.direa.backoffice.dto.ApprovalCommentDto;
import kr.co.direa.backoffice.repository.ApprovalCommentRepository;
import kr.co.direa.backoffice.repository.ApprovalRequestRepository;
import kr.co.direa.backoffice.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApprovalCommentService {
	private final ApprovalCommentRepository approvalCommentRepository;
	private final ApprovalRequestRepository approvalRequestRepository;
	private final UsersRepository usersRepository;
	private final NotificationService notificationService;
	private final CommonLookupService commonLookupService;
	// private final MailService mailService; // Mail sending disabled during local development

	@Transactional
	public ApprovalCommentDto addComment(Long approvalId, String username, String content) {
		if (content == null || content.isBlank()) {
			throw new IllegalArgumentException("Comment content must not be empty");
		}
		ApprovalRequest approval = approvalRequestRepository.findById(approvalId)
			.orElseThrow(() -> new IllegalArgumentException("Approval not found: " + approvalId));
		Users user = usersRepository.findByUsername(username).orElse(null);
		UUID externalId = commonLookupService.resolveKeycloakUserIdByUsername(username)
			.map(this::safeUuid)
			.orElse(null);

		ApprovalComment comment = ApprovalComment.builder()
			.request(approval)
			.user(user)
			.content(content)
			.authorName(user != null ? user.getUsername() : username)
			.authorEmail(user != null ? user.getEmail() : null)
			.authorExternalId(externalId)
			.build();
		ApprovalComment saved = approvalCommentRepository.save(comment);
		notifyParticipants(approval, saved);
		return new ApprovalCommentDto(saved);
	}

	@Transactional(readOnly = true)
	public List<ApprovalCommentDto> findByApproval(Long approvalId) {
		return approvalCommentRepository.findByRequestIdOrderByCreatedDateAsc(approvalId).stream()
				.map(ApprovalCommentDto::new)
				.toList();
	}

	private void notifyParticipants(ApprovalRequest approval, ApprovalComment comment) {
		DeviceApprovalDetail detail = approval.getDetail() instanceof DeviceApprovalDetail deviceDetail
			? deviceDetail
			: null;
		Devices device = detail != null ? detail.getDevice() : null;
		String subject = "[장비 결재 댓글] " + buildApprovalSubject(approval, device);
		String link = "/approvals/" + approval.getId();

		Set<String> receivers = new HashSet<>();
		Optional.ofNullable(approval.getRequester())
			.map(Users::getUsername)
			.filter(name -> name != null && !name.isBlank())
			.ifPresent(receivers::add);
		Optional.ofNullable(approval.getRequesterName())
			.filter(name -> name != null && !name.isBlank())
			.ifPresent(receivers::add);

		if (approval.getSteps() != null) {
			for (ApprovalStep step : approval.getSteps()) {
				Optional.ofNullable(step.getApprover())
					.map(Users::getUsername)
					.filter(name -> name != null && !name.isBlank())
					.ifPresent(receivers::add);
				Optional.ofNullable(step.getApproverName())
					.filter(name -> name != null && !name.isBlank())
					.ifPresent(receivers::add);
			}
		}

		Optional.ofNullable(comment.getUser())
			.map(Users::getUsername)
			.filter(name -> name != null && !name.isBlank())
			.ifPresent(receivers::remove);
		Optional.ofNullable(comment.getAuthorName())
			.filter(name -> name != null && !name.isBlank())
			.ifPresent(receivers::remove);

		for (String receiver : receivers) {
			notificationService.createNotification(receiver, subject, Constants.COMMENT_TYPE, link);
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

	private String buildApprovalSubject(ApprovalRequest approval, Devices device) {
		String deviceId = device != null ? device.getId() : "";
		if (!deviceId.isBlank()) {
			return deviceId;
		}
		return Optional.ofNullable(approval.getTitle())
			.filter(title -> !title.isBlank())
			.orElse("승인 ID " + approval.getId());
	}
}
