package ru.murad.yourmarket.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "telegram_users")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class TelegramUser extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "telegram_user_id", nullable = false, unique = true)
    private Long telegramUserId;
    @Column(name = "chat_id", nullable = false)
    private Long chatId;
    private String username;
    @Column(name = "first_name")
    private String firstName;
    @Column(nullable = false)
    private boolean blocked;
}
