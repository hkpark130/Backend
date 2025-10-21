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
