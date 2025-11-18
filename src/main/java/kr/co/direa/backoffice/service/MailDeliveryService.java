package kr.co.direa.backoffice.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Slf4j
@Service
@RequiredArgsConstructor
class MailDeliveryService {
	private final JavaMailSender mailSender;
	private final TemplateEngine templateEngine;

	@Value("${spring.mail.username:}")
	private String mailUsername;

	@Async("mailTaskExecutor")
	public void send(String to,
			String subject,
			String templateName,
			Map<String, Object> variables) {
		if (!StringUtils.hasText(to)) {
			log.debug("메일 수신자가 없어 전송을 건너뜁니다. subject={}", subject);
			return;
		}
		try {
			MimeMessage message = mailSender.createMimeMessage();
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setTo(to.trim());
			String fromAddress = resolveFromAddress();
			if (StringUtils.hasText(fromAddress)) {
				helper.setFrom(Objects.requireNonNull(fromAddress));
			}
			helper.setSubject(subject);
			Context context = new Context(Locale.KOREAN);
			context.setVariables(variables != null ? variables : Map.of());
			String html = templateEngine.process(templateName, context);
			helper.setText(html, true);
			mailSender.send(message);
			log.debug("메일 전송 완료 - to={}, subject={}", to, subject);
		} catch (MessagingException ex) {
			log.error("메일 전송 실패 - to={}, subject={}", to, subject, ex);
		} catch (RuntimeException ex) {
			log.error("템플릿 처리 중 예외 - to={}, subject={}", to, subject, ex);
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
