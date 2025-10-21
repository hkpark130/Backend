package kr.co.direa.backoffice.service;

import kr.co.direa.backoffice.domain.Notifications;
import kr.co.direa.backoffice.dto.NotificationDto;
import kr.co.direa.backoffice.repository.NotificationsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {
	private final NotificationsRepository notificationsRepository;

	@Transactional
	public void createNotification(String receiver, String subject, String type, String link) {
		if (receiver == null || receiver.isBlank()) {
			return;
		}
		Notifications notification = Notifications.builder()
				.receiver(receiver)
				.subject(subject)
				.type(type)
				.link(link)
				.build();
		notificationsRepository.save(notification);
	}

	@Transactional(readOnly = true)
	public List<NotificationDto> findByReceiver(String receiver) {
		return notificationsRepository.findByReceiverOrderByCreatedDateDesc(receiver).stream()
				.map(notification -> new NotificationDto(notification).applyIcon())
				.toList();
	}

	@Transactional
	public void markAsRead(Long notificationId) {
		notificationsRepository.findById(notificationId)
				.ifPresent(notification -> {
					notification.markAsRead();
					notificationsRepository.save(notification);
				});
	}
}
