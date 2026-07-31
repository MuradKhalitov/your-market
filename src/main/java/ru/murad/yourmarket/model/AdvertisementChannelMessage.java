package ru.murad.yourmarket.model;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;
import java.time.Instant;
import ru.murad.yourmarket.model.enums.ChannelMessageStatus;
import ru.murad.yourmarket.model.enums.TelegramMessageType;
@Entity @Table(name="advertisement_channel_messages", uniqueConstraints=@UniqueConstraint(columnNames={"advertisement_id","position"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AdvertisementChannelMessage {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="advertisement_id",nullable=false) private UUID advertisementId;
    @Column(name="channel_message_id",nullable=false) private Integer channelMessageId;
    @Column(nullable=false) private Integer position;
    @Column(name="publication_operation_id") private UUID publicationOperationId;
    @Enumerated(EnumType.STRING) @Column(name="deletion_status",nullable=false) @Builder.Default private ChannelMessageStatus deletionStatus=ChannelMessageStatus.ACTIVE;
    @Column(name="deletion_started_at") private Instant deletionStartedAt;
    @Column(name="deleted_at") private Instant deletedAt;
    @Column(name="delete_attempts",nullable=false) private int deleteAttempts;
    @Column(name="last_delete_error",length=500) private String lastDeleteError;
    @Column(name="delete_operation_id") private UUID deleteOperationId;
    @Enumerated(EnumType.STRING) @Column(name="message_type",nullable=false) @Builder.Default
    private TelegramMessageType messageType=TelegramMessageType.PHOTO;
}
