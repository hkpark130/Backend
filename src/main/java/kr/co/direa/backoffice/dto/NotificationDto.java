package kr.co.direa.backoffice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import kr.co.direa.backoffice.constant.Constants;
import kr.co.direa.backoffice.domain.Notifications;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Getter
@NoArgsConstructor
public class NotificationDto implements Serializable {
    private Long id;
    private String subject;
    private String link;
    @JsonProperty("is_read")
    private boolean isRead;
    private String icon;
    private String userName;
    private String iconClass;
    private String date;
    private String type;
    private String receiver;

    public NotificationDto(Notifications entity) {
        this.id = entity.getId();
        this.subject = entity.getSubject();
        this.link = entity.getLink();
        this.isRead = Boolean.TRUE.equals(entity.getIsRead());
        this.type = entity.getType();
        this.date = entity.getCreatedDate().format(DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm"));
        this.receiver = entity.getReceiver();
    }

    public Notifications toEntity() {
        return Notifications.builder()
                .subject(subject)
                .link(link)
                .type(type)
                .receiver(receiver)
                .build();
    }

    public String formatCreatedDate(LocalDateTime createdDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");
        return createdDate.format(formatter);
    }

    public NotificationDto applyIcon() {
        switch (type) {
            case Constants.APPROVAL_RENTAL -> {
                icon = "inbox";
                iconClass = "primary";
            }
            case Constants.APPROVAL_RETURN -> {
                icon = "corner-down-left";
                iconClass = "primary";
            }
            case Constants.NOTIFICATION_APPROVAL -> {
                icon = "check-square";
                iconClass = "primary";
            }
            case Constants.DISPOSE_TYPE -> {
                icon = "trash";
                iconClass = "secondary";
            }
            case Constants.PURCHASE_TYPE -> {
                icon = "dollar-sign";
                iconClass = "primary";
            }
            case Constants.EDIT_TYPE -> {
                icon = "edit";
                iconClass = "success";
            }
            case Constants.COMMENT_TYPE -> {
                icon = "message-square";
                iconClass = "primary";
            }
            default -> {
                icon = "bell";
                iconClass = "secondary";
            }
        }
        return this;
    }
}
