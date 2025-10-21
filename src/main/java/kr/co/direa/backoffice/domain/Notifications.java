package kr.co.direa.backoffice.domain;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "notifications")
public class Notifications extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "subject")
    private String subject;

    @Column(name = "link")
    private String link = "#";

    @Column(name = "is_read")
    private Boolean isRead = Boolean.FALSE;

    @Column(name = "type")
    private String type;

    @Column(name = "receiver")
    private String receiver;

    @Builder
    public Notifications(String subject, String link, String type, String receiver) {
        this.subject = subject;
        this.link = link;
        this.type = type;
        this.receiver = receiver;
    }

    public void markAsRead() {
        this.isRead = Boolean.TRUE;
    }
}
