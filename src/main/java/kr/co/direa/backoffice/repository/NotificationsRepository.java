package kr.co.direa.backoffice.repository;

import kr.co.direa.backoffice.domain.Notifications;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationsRepository extends JpaRepository<Notifications, Long> {

    List<Notifications> findByReceiverOrderByCreatedDateDesc(String receiver);
}
