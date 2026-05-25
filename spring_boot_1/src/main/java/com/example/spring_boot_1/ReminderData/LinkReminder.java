package com.example.spring_boot_1.ReminderData;

import com.example.spring_boot_1.LinkData.LinkData;
import com.example.spring_boot_1.UserData.UserData;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(
        name = "link_reminders",
        indexes = {
                @Index(name = "idx_link_reminders_link_sent", columnList = "link_data_id, sent_at"),
                @Index(name = "idx_link_reminders_user_sent", columnList = "user_id, sent_at")
        }
)
public class LinkReminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "link_data_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private LinkData linkData;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private UserData userData;

    @Column(nullable = false)
    private String channel;

    @Column(nullable = false)
    private String mode;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "snoozed_until")
    private LocalDateTime snoozedUntil;

    @PrePersist
    public void setDefaults() {
        if (sentAt == null) {
            sentAt = LocalDateTime.now();
        }
    }
}
