package kr.co.direa.backoffice.controller;

import kr.co.direa.backoffice.dto.NotificationDto;
import kr.co.direa.backoffice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping("/notifications")
    // TODO 인증 연동 시: 로그인 사용자 본인의 알림만 조회할 수 있도록 Security 필터 추가 필요
    public ResponseEntity<List<NotificationDto>> getNotifications(@RequestParam String receiver) {
        return ResponseEntity.ok(notificationService.findByReceiver(receiver));
    }

    @PostMapping("/notifications/{notificationId}/read")
    // TODO 인증 연동 시: 알림 수신자만 읽음 처리할 수 있도록 권한 검증 필요
    public ResponseEntity<Void> markAsRead(@PathVariable Long notificationId) {
        notificationService.markAsRead(notificationId);
        return ResponseEntity.noContent().build();
    }
}
