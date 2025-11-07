package kr.co.direa.backoffice.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import kr.co.direa.backoffice.config.ApprovalProperties;
import kr.co.direa.backoffice.domain.ApprovalComment;
import kr.co.direa.backoffice.domain.ApprovalRequest;
import kr.co.direa.backoffice.domain.ApprovalStep;
import kr.co.direa.backoffice.domain.DeviceApprovalDetail;
import kr.co.direa.backoffice.domain.Devices;
import kr.co.direa.backoffice.domain.Departments;
import kr.co.direa.backoffice.domain.Projects;
import kr.co.direa.backoffice.domain.enums.DeviceApprovalAction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {
	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

	private final JavaMailSender mailSender;
	private final TemplateEngine templateEngine;
	private final CommonLookupService commonLookupService;
	private final ApprovalProperties approvalProperties;

	@Value("${spring.mail.username:}")
	private String mailUsername;

	@Value("${constants.frontend:http://localhost}")
	private String frontendBaseUrl;

	public void sendPasswordResetMail(String username, String temporaryPassword) {
		resolveUserInfoWithEmail(null, username).ifPresent(userInfo -> {
			Map<String, Object> variables = new HashMap<>();
			variables.put("displayName", displayName(userInfo, username));
			variables.put("temporaryPassword", temporaryPassword);
			variables.put("loginLink", buildAbsoluteLink("/login"));
			variables.put("issuedAt", formatDateTime(LocalDateTime.now()));
			sendEmail(userInfo.email(), "[디리아] 임시 비밀번호 발급 안내", "password-reset", variables);
		});
	}

	public void sendApprovalAwaitingMail(ApprovalRequest approval, ApprovalStep step, String relativeLink, String message) {
		if (approval == null || step == null) {
			return;
		}
		Optional<ApprovalProperties.Stage> defaultStage = findDefaultApprover(step.getApproverExternalId(), step.getApproverName());
		String fallbackName = resolveApproverName(step, defaultStage);
		String directEmail = normalizeEmail(step.getApproverEmail());
		if (StringUtils.hasText(directEmail)) {
			Map<String, Object> variables = baseApprovalVariables(approval);
			variables.put("displayName", displayName(null, fallbackName));
			variables.put("stageLabel", stageLabel(step.getSequence()));
			variables.put("additionalMessage", message != null ? message : "");
			variables.put("link", buildAbsoluteLink(relativeLink));
			variables.put("linkLabel", "신청 상세 보기");
			String subject = String.format("[장비 결재 요청] %s - %s", variables.get("actionName"), variables.get("deviceId"));
			sendEmail(directEmail, subject, "approval-awaiting", variables);
			return;
		}

		resolveUserInfoWithEmail(step.getApproverExternalId(), step.getApproverName()).ifPresent(userInfo -> {
			Map<String, Object> variables = baseApprovalVariables(approval);
			variables.put("displayName", displayName(userInfo, fallbackName));
			variables.put("stageLabel", stageLabel(step.getSequence()));
			variables.put("additionalMessage", message != null ? message : "");
			variables.put("link", buildAbsoluteLink(relativeLink));
			variables.put("linkLabel", "신청 상세 보기");
			String subject = String.format("[장비 결재 요청] %s - %s", variables.get("actionName"), variables.get("deviceId"));
			sendEmail(userInfo.email(), subject, "approval-awaiting", variables);
		});
	}

	public void sendApprovalUpdatedMail(ApprovalRequest approval, ApprovalStep firstStep, String relativeLink) {
		if (approval == null || firstStep == null) {
			return;
		}
		Optional<ApprovalProperties.Stage> defaultStage = findDefaultApprover(firstStep.getApproverExternalId(), firstStep.getApproverName());
		String fallbackName = resolveApproverName(firstStep, defaultStage);
		String directEmail = normalizeEmail(firstStep.getApproverEmail());
		if (StringUtils.hasText(directEmail)) {
			Map<String, Object> variables = baseApprovalVariables(approval);
			variables.put("displayName", displayName(null, fallbackName));
			variables.put("stageLabel", stageLabel(firstStep.getSequence()));
			variables.put("link", buildAbsoluteLink(relativeLink));
			variables.put("linkLabel", "수정된 신청 확인하기");
			variables.put("additionalMessage", "신청자가 내용을 수정했습니다. 확인 후 필요한 조치를 진행해 주세요.");
			sendEmail(directEmail, "[장비 결재 수정] 신청 내용이 변경되었습니다.", "approval-updated", variables);
			return;
		}

		resolveUserInfoWithEmail(firstStep.getApproverExternalId(), firstStep.getApproverName()).ifPresent(userInfo -> {
			Map<String, Object> variables = baseApprovalVariables(approval);
			variables.put("displayName", displayName(userInfo, fallbackName));
			variables.put("stageLabel", stageLabel(firstStep.getSequence()));
			variables.put("link", buildAbsoluteLink(relativeLink));
			variables.put("linkLabel", "수정된 신청 확인하기");
			variables.put("additionalMessage", "신청자가 내용을 수정했습니다. 확인 후 필요한 조치를 진행해 주세요.");
			sendEmail(userInfo.email(), "[장비 결재 수정] 신청 내용이 변경되었습니다.", "approval-updated", variables);
		});
	}

	public void sendApprovalCompletedMail(ApprovalRequest approval, String relativeLink) {
		if (approval == null) {
			return;
		}
		resolveRequesterWithEmail(approval).ifPresent(userInfo -> {
			Map<String, Object> variables = baseApprovalVariables(approval);
			variables.put("displayName", displayName(userInfo, approval.getRequesterName()));
			variables.put("link", buildAbsoluteLink(relativeLink));
			variables.put("linkLabel", "신청 상세 보기");
			sendEmail(userInfo.email(), "[장비 결재 완료] 신청이 승인되었습니다.", "approval-completed", variables);
		});
	}

	public void sendApprovalRejectedMail(ApprovalRequest approval, String rejectionReason, String relativeLink) {
		if (approval == null) {
			return;
		}
		resolveRequesterWithEmail(approval).ifPresent(userInfo -> {
			Map<String, Object> variables = baseApprovalVariables(approval);
			variables.put("displayName", displayName(userInfo, approval.getRequesterName()));
			variables.put("rejectionReason", StringUtils.hasText(rejectionReason) ? rejectionReason : "");
			variables.put("link", buildAbsoluteLink(relativeLink));
			variables.put("linkLabel", "신청 상세 보기");
			sendEmail(userInfo.email(), "[장비 결재 반려] 신청이 반려되었습니다.", "approval-rejected", variables);
		});
	}

	public void sendApprovalCommentMail(ApprovalRequest approval,
			ApprovalComment comment,
			Map<String, String> receiversWithLink) {
		if (approval == null || comment == null || receiversWithLink == null || receiversWithLink.isEmpty()) {
			return;
		}
		String authorDisplay = resolveDisplayName(comment.getAuthorExternalId(), comment.getAuthorName());
		String commentCreatedAt = formatDateTime(comment.getCreatedDate());
		receiversWithLink.forEach((receiver, relativeLink) ->
			resolveUserInfoWithEmail(null, receiver).ifPresent(userInfo -> {
				Map<String, Object> variables = baseApprovalVariables(approval);
				variables.put("displayName", displayName(userInfo, receiver));
				variables.put("commentAuthor", authorDisplay);
				variables.put("commentContent", defaultString(comment.getContent()));
				variables.put("commentCreatedAt", commentCreatedAt);
				variables.put("link", buildAbsoluteLink(relativeLink));
				variables.put("linkLabel", "댓글 확인하기");
				sendEmail(userInfo.email(), "[장비 결재 댓글] 새로운 댓글이 등록되었습니다.", "approval-comment", variables);
			})
		);
	}

	private Map<String, Object> baseApprovalVariables(ApprovalRequest approval) {
		Map<String, Object> variables = new HashMap<>();
		variables.put("approvalId", approval.getId());
		variables.put("title", defaultString(approval.getTitle()));
		variables.put("reason", defaultString(approval.getReason()));
		variables.put("submittedAt", formatDateTime(approval.getSubmittedAt()));
		variables.put("deadline", formatDateTime(approval.getDueDate()));
		variables.put("status", approval.getStatus() != null ? approval.getStatus().name() : "");
		variables.put("requesterName", defaultString(approval.getRequesterName()));

		DeviceApprovalDetail detail = approval.getDetail() instanceof DeviceApprovalDetail deviceDetail ? deviceDetail : null;
		if (detail != null) {
			DeviceApprovalAction action = detail.getAction();
			Devices device = detail.getDevice();
			Projects project = detail.getRequestedProject();
			Departments department = detail.getRequestedDepartment();
			variables.put("actionName", action != null ? defaultString(action.getDisplayName()) : "");
			variables.put("deviceId", device != null ? defaultString(device.getId()) : "");
			variables.put("requestedStatus", defaultString(detail.getRequestedStatus()));
			variables.put("requestedPurpose", defaultString(detail.getRequestedPurpose()));
			variables.put("requestedRealUser", defaultString(detail.getRequestedRealUser()));
			variables.put("requestedProject", project != null ? defaultString(project.getName()) : "");
			variables.put("requestedProjectCode", project != null ? defaultString(project.getCode()) : "");
			variables.put("requestedDepartment", department != null ? defaultString(department.getName()) : "");
			variables.put("memo", defaultString(detail.getMemo()));
			variables.put("usagePeriod", formatUsagePeriod(detail.getRequestedUsageStartDate(), detail.getRequestedUsageEndDate()));
		} else {
			variables.put("actionName", "");
			variables.put("deviceId", "");
			variables.put("requestedStatus", "");
			variables.put("requestedPurpose", "");
			variables.put("requestedRealUser", "");
			variables.put("requestedProject", "");
			variables.put("requestedProjectCode", "");
			variables.put("requestedDepartment", "");
			variables.put("usagePeriod", "");
			variables.put("memo", "");
		}
		return variables;
	}

	private Optional<CommonLookupService.KeycloakUserInfo> resolveRequesterWithEmail(ApprovalRequest approval) {
		if (approval == null) {
			return Optional.empty();
		}
		UUID externalId = approval.getRequesterExternalId();
		String requesterName = approval.getRequesterName();
		Optional<CommonLookupService.KeycloakUserInfo> info = Optional.empty();
		if (externalId != null) {
			info = commonLookupService.resolveKeycloakUserInfoById(externalId)
				.filter(user -> StringUtils.hasText(user.email()));
		}
		if (info.isPresent()) {
			return info;
		}
		if (StringUtils.hasText(requesterName)) {
			return commonLookupService.resolveKeycloakUserInfoByUsername(requesterName.trim())
				.filter(user -> StringUtils.hasText(user.email()));
		}
		return Optional.empty();
	}

	private Optional<CommonLookupService.KeycloakUserInfo> resolveUserInfoWithEmail(@Nullable UUID externalId,
			@Nullable String username) {
		Optional<ApprovalProperties.Stage> defaultStage = findDefaultApprover(externalId, username);
		if (defaultStage.isPresent()) {
			ApprovalProperties.Stage stage = defaultStage.get();
			if (StringUtils.hasText(stage.getEmail())) {
				return Optional.of(toUserInfoFromStage(stage, username));
			}
		}

		UUID effectiveExternalId = externalId;
		if (defaultStage.isPresent() && defaultStage.get().getKeycloakId() != null) {
			effectiveExternalId = defaultStage.get().getKeycloakId();
		}

		String effectiveUsername = username;
		if (defaultStage.isPresent()) {
			effectiveUsername = firstNonBlank(defaultStage.get().getUsername(), username, defaultStage.get().getDisplayName());
		}

		if (effectiveExternalId != null) {
			Optional<CommonLookupService.KeycloakUserInfo> info = commonLookupService.resolveKeycloakUserInfoById(effectiveExternalId);
			if (info.isPresent() && StringUtils.hasText(info.get().email())) {
				return info;
			}
			if (info.isPresent() && defaultStage.isEmpty()) {
				return info;
			}
		}

		if (StringUtils.hasText(effectiveUsername)) {
			Optional<CommonLookupService.KeycloakUserInfo> info = commonLookupService.resolveKeycloakUserInfoByUsername(effectiveUsername.trim());
			if (info.isPresent() && StringUtils.hasText(info.get().email())) {
				return info;
			}
			if (info.isPresent()) {
				return info;
			}
		}

		if (defaultStage.isPresent()) {
			return Optional.of(toUserInfoFromStage(defaultStage.get(), effectiveUsername));
		}
		return Optional.empty();
	}

	private String displayName(CommonLookupService.KeycloakUserInfo userInfo, @Nullable String fallback) {
		if (userInfo != null) {
			if (StringUtils.hasText(userInfo.displayName())) {
				return userInfo.displayName();
			}
			if (StringUtils.hasText(userInfo.username())) {
				return userInfo.username();
			}
		}
		return fallback != null ? fallback : "사용자";
	}

	private String resolveDisplayName(@Nullable UUID externalId, @Nullable String fallback) {
		if (externalId != null) {
			Optional<CommonLookupService.KeycloakUserInfo> info = commonLookupService.resolveKeycloakUserInfoById(externalId);
			if (info.isPresent()) {
				return displayName(info.get(), fallback);
			}
		}
		if (StringUtils.hasText(fallback)) {
			Optional<CommonLookupService.KeycloakUserInfo> info = commonLookupService.resolveKeycloakUserInfoByUsername(fallback.trim());
			if (info.isPresent()) {
				return displayName(info.get(), fallback);
			}
		}
		return fallback != null ? fallback : "사용자";
	}

	private Optional<ApprovalProperties.Stage> findDefaultApprover(@Nullable UUID externalId, @Nullable String candidate) {
		List<ApprovalProperties.Stage> stages = approvalProperties.getDefaultApprovers();
		if (stages == null || stages.isEmpty()) {
			return Optional.empty();
		}
		return stages.stream()
			.filter(stage -> matchesStage(externalId, candidate, stage))
			.findFirst();
	}

	private boolean matchesStage(@Nullable UUID externalId, @Nullable String candidate, ApprovalProperties.Stage stage) {
		if (externalId != null && stage.getKeycloakId() != null && stage.getKeycloakId().equals(externalId)) {
			return true;
		}
		return matchesCandidate(candidate, stage.getUsername()) || matchesCandidate(candidate, stage.getDisplayName());
	}

	private boolean matchesCandidate(@Nullable String candidate, @Nullable String value) {
		return StringUtils.hasText(candidate) && StringUtils.hasText(value)
			&& candidate.trim().equalsIgnoreCase(value.trim());
	}

	private String resolveApproverName(@Nullable ApprovalStep step, Optional<ApprovalProperties.Stage> defaultStage) {
		String candidate = defaultStage
			.map(stage -> firstNonBlank(stage.getDisplayName(), stage.getUsername(), step != null ? step.getApproverName() : null))
			.orElse(step != null ? step.getApproverName() : null);
		if (!StringUtils.hasText(candidate)) {
			return null;
		}
		return candidate.trim();
	}

	private CommonLookupService.KeycloakUserInfo toUserInfoFromStage(ApprovalProperties.Stage stage, @Nullable String fallbackUsername) {
		String username = firstNonBlank(stage.getUsername(), fallbackUsername, stage.getDisplayName());
		String displayName = firstNonBlank(stage.getDisplayName(), stage.getUsername(), fallbackUsername);
		String email = normalizeEmail(stage.getEmail());
		return new CommonLookupService.KeycloakUserInfo(stage.getKeycloakId(), username, email, displayName);
	}

	private String normalizeEmail(@Nullable String email) {
		if (!StringUtils.hasText(email)) {
			return null;
		}
		return email.trim();
	}

	private String firstNonBlank(String... values) {
		if (values == null) {
			return null;
		}
		for (String value : values) {
			if (StringUtils.hasText(value)) {
				return value.trim();
			}
		}
		return null;
	}

	private String buildAbsoluteLink(@Nullable String relative) {
		if (StringUtils.hasText(relative) && (relative.startsWith("http://") || relative.startsWith("https://"))) {
			return relative;
		}
		String base = StringUtils.hasText(frontendBaseUrl) ? frontendBaseUrl.trim() : "";
		String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
		String path = StringUtils.hasText(relative) ? relative.trim() : "/";
		String normalizedPath = path.startsWith("/") ? path : "/" + path;
		return normalizedBase + normalizedPath;
	}

	private String stageLabel(int sequence) {
		if (sequence <= 0) {
			return "결재";
		}
		return sequence + "차 결재";
	}

	private String formatUsagePeriod(@Nullable LocalDateTime start, @Nullable LocalDateTime end) {
		if (start == null && end == null) {
			return "";
		}
		if (start != null && end != null) {
			return formatDate(start) + " ~ " + formatDate(end);
		}
		if (start != null) {
			return formatDate(start) + " ~";
		}
		return "~ " + formatDate(end);
	}

	private String formatDate(@Nullable LocalDateTime date) {
		if (date == null) {
			return "";
		}
		return DATE_TIME_FORMATTER.format(date);
	}

	private String formatDateTime(@Nullable LocalDateTime dateTime) {
		if (dateTime == null) {
			return "";
		}
		return DATE_TIME_FORMATTER.format(dateTime);
	}

	private String defaultString(@Nullable String value) {
		return value == null ? "" : value;
	}

	private void sendEmail(String to,
			String subject,
			String templateName,
			Map<String, Object> variables) {
		if (!StringUtils.hasText(to)) {
			return;
		}
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setTo(to);
			if (StringUtils.hasText(resolveFromAddress())) {
				helper.setFrom(Objects.requireNonNull(resolveFromAddress()));
			}
			helper.setSubject(subject);
			Context context = new Context(Locale.KOREAN);
			context.setVariables(variables);
			String html = templateEngine.process(templateName, context);
			helper.setText(html, true);
			mailSender.send(message);
			log.debug("Mail sent to {} with subject {}", to, subject);
		} catch (MessagingException ex) {
			log.error("Failed to send mail to {} with subject {}", to, subject, ex);
		}
	}

	@Nullable
	private String resolveFromAddress() {
		if (!StringUtils.hasText(mailUsername)) {
			return null;
		}
		String trimmed = mailUsername.trim();
		if (trimmed.contains("@")) {
			return trimmed;
		}
		return null;
	}
}
/*
package kr.co.direa.backoffice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {
	private final JavaMailSender mailSender;

	public void sendMail(String to, String subject, String body) {
		if (to == null || to.isBlank()) {
			log.warn("Skip sending mail: empty recipient for subject {}", subject);
			return;
		}

		try {
			SimpleMailMessage message = new SimpleMailMessage();
			message.setTo(to);
			message.setSubject(subject);
			message.setText(body);
			mailSender.send(message);
			log.debug("Mail sent to {} with subject {}", to, subject);
		} catch (Exception ex) {
			log.error("Failed to send mail to {} with subject {}", to, subject, ex);
		}
	}
}
*/
