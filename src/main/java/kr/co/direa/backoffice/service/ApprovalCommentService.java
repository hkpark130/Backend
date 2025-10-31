package kr.co.direa.backoffice.service;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import kr.co.direa.backoffice.constant.Constants;
import kr.co.direa.backoffice.domain.ApprovalComment;
import kr.co.direa.backoffice.domain.ApprovalRequest;
import kr.co.direa.backoffice.domain.ApprovalStep;
import kr.co.direa.backoffice.domain.DeviceApprovalDetail;
import kr.co.direa.backoffice.domain.Devices;
import kr.co.direa.backoffice.dto.ApprovalCommentDto;
import kr.co.direa.backoffice.repository.ApprovalCommentRepository;
import kr.co.direa.backoffice.repository.ApprovalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApprovalCommentService {
	private final ApprovalCommentRepository approvalCommentRepository;
	private final ApprovalRequestRepository approvalRequestRepository;
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
		CommonLookupService.KeycloakUserInfo userInfo = resolveUserInfo(username);
		String trimmedContent = content.trim();
		String resolvedName = safeUsername(userInfo, username);
		UUID externalId = Optional.ofNullable(userInfo)
			.map(CommonLookupService.KeycloakUserInfo::id)
			.orElse(null);

		ApprovalComment comment = ApprovalComment.builder()
			.request(approval)
			.content(trimmedContent)
			.authorName(resolvedName)
			.authorEmail(Optional.ofNullable(userInfo)
				.map(CommonLookupService.KeycloakUserInfo::email)
				.orElse(null))
			.authorExternalId(externalId)
			.build();
		ApprovalComment saved = approvalCommentRepository.save(comment);
		notifyParticipants(approval, saved);
		return new ApprovalCommentDto(saved);
	}

	@Transactional
	public ApprovalCommentDto updateComment(Long approvalId, Long commentId, String username, String content) {
		if (content == null || content.isBlank()) {
			throw new IllegalArgumentException("Comment content must not be empty");
		}
		ApprovalComment comment = resolveComment(approvalId, commentId);
		ensureOwnership(comment, username);
		String trimmedContent = content.trim();
		comment.setContent(trimmedContent);
		String normalizedUsername = username != null ? username.trim() : null;
		if ((comment.getAuthorName() == null || comment.getAuthorName().isBlank())
				&& normalizedUsername != null && !normalizedUsername.isEmpty()) {
			comment.setAuthorName(normalizedUsername);
		}
		ApprovalComment saved = approvalCommentRepository.save(comment);
		return new ApprovalCommentDto(saved);
	}

	@Transactional
	public void deleteComment(Long approvalId, Long commentId, String username) {
		ApprovalComment comment = resolveComment(approvalId, commentId);
		ensureOwnership(comment, username);
		approvalCommentRepository.delete(comment);
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
		String link = "/admin/approvals/" + approval.getId();

		Set<String> receivers = new HashSet<>();
		Optional.ofNullable(approval.getRequesterName())
			.filter(name -> name != null && !name.isBlank())
			.ifPresent(receivers::add);

		if (approval.getSteps() != null) {
			for (ApprovalStep step : approval.getSteps()) {
				Optional.ofNullable(step.getApproverName())
					.filter(name -> name != null && !name.isBlank())
					.ifPresent(receivers::add);
			}
		}

		Optional.ofNullable(comment.getAuthorName())
			.filter(name -> name != null && !name.isBlank())
			.ifPresent(receivers::remove);

		for (String receiver : receivers) {
			notificationService.createNotification(receiver, subject, Constants.COMMENT_TYPE, link);
		}
	}

	private ApprovalComment resolveComment(Long approvalId, Long commentId) {
		ApprovalComment comment = approvalCommentRepository.findById(commentId)
			.orElseThrow(() -> new IllegalArgumentException("Approval comment not found: " + commentId));
		Long requestId = Optional.ofNullable(comment.getRequest()).map(ApprovalRequest::getId).orElse(null);
		if (!Objects.equals(requestId, approvalId)) {
			throw new IllegalArgumentException("Comment does not belong to approval: " + approvalId);
		}
		return comment;
	}

	private void ensureOwnership(ApprovalComment comment, String username) {
		String normalized = username != null ? username.trim() : "";
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Username must not be empty");
		}
		CommonLookupService.KeycloakUserInfo requesterInfo = resolveUserInfo(username);
		UUID requesterExternalId = Optional.ofNullable(requesterInfo)
			.map(CommonLookupService.KeycloakUserInfo::id)
			.orElse(null);
		UUID authorExternalId = comment.getAuthorExternalId();
		if (authorExternalId != null && requesterExternalId != null && authorExternalId.equals(requesterExternalId)) {
			return;
		}
		String authorName = Optional.ofNullable(comment.getAuthorName()).orElse("").trim();
		if (!authorName.isEmpty() && authorName.equalsIgnoreCase(normalized)) {
			return;
		}
		throw new IllegalStateException("You do not have permission to modify this comment");
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

	private CommonLookupService.KeycloakUserInfo resolveUserInfo(String username) {
		if (username == null || username.isBlank()) {
			return null;
		}
		return commonLookupService.resolveKeycloakUserInfoByUsername(username)
			.orElseGet(() -> commonLookupService.fallbackUserInfo(username));
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
}
