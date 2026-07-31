package ru.murad.yourmarket.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import ru.murad.yourmarket.model.enums.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "advertisements")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Advertisement extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;
    @Column(name = "chat_id", nullable = false)
    private Long chatId;
    private String username;
    @NotNull @Enumerated(EnumType.STRING) @Column(nullable = false)
    private AdvertisementCategory category;
    @NotBlank @Size(min = 3, max = 150)
    @Column(nullable = false)
    private String title;
    @NotBlank @Size(min = 10, max = 2000)
    @Column(nullable = false, length = 2000)
    private String description;
    @NotNull @DecimalMin("0.01") @DecimalMax("999999999.99")
    @Column(name = "item_price", nullable = false, precision = 11, scale = 2)
    private BigDecimal itemPrice;
    @NotBlank
    @Column(name = "telegram_file_id", nullable = false)
    private String telegramFileId;
    @NotBlank @Size(min = 2, max = 100)
    @Column(nullable = false)
    private String city;
    @NotBlank @Size(min = 2, max = 255)
    @Column(nullable = false)
    private String contact;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private AdvertisementStatus status;
    @Column(name = "channel_message_id")
    private Integer channelMessageId;
    @Column(name = "paid_at")
    private Instant paidAt;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;
    @Column(name = "expires_at")
    private Instant expiresAt;
    @Column(name = "expired_at")
    private Instant expiredAt;
    @Column(name = "rejected_at")
    private Instant rejectedAt;
    @Column(name = "rejection_reason")
    private String rejectionReason;
    @Column(name = "publication_operation_id")
    private UUID publicationOperationId;
    @Column(name = "publication_started_at")
    private Instant publicationStartedAt;
    @Enumerated(EnumType.STRING) @Column(name = "publication_phase")
    private PublicationPhase publicationPhase;
    @Column(name = "publication_updated_at")
    private Instant publicationUpdatedAt;
    @Column(name = "publication_failure_reason", length = 500)
    private String publicationFailureReason;
    @Column(name = "moderation_message_id")
    private Integer moderationMessageId;
    @Column(name = "moderation_submitted_at")
    private Instant moderationSubmittedAt;
    @Enumerated(EnumType.STRING) @Column(name = "moderation_submission_status", nullable = false)
    @Builder.Default private ModerationSubmissionStatus moderationSubmissionStatus = ModerationSubmissionStatus.NOT_SUBMITTED;
    @Column(name = "moderation_sending_since")
    private Instant moderationSendingSince;
    @Column(name = "moderation_operation_id")
    private UUID moderationOperationId;
    @Enumerated(EnumType.STRING) @Column(name="moderation_phase") private ModerationPhase moderationPhase;
    @Column(name="expiration_operation_id") private UUID expirationOperationId;
    @Column(name="expiration_started_at") private Instant expirationStartedAt;
}
