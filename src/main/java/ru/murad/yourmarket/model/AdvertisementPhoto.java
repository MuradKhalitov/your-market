package ru.murad.yourmarket.model;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="advertisement_photos", uniqueConstraints=@UniqueConstraint(columnNames={"advertisement_id","position"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AdvertisementPhoto {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="advertisement_id",nullable=false) private UUID advertisementId;
    @Column(name="telegram_file_id",nullable=false) private String telegramFileId;
    @Column(nullable=false) private Integer position;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @PrePersist void create(){if(createdAt==null)createdAt=Instant.now();}
}
