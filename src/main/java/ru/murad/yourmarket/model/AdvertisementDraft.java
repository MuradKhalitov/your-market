package ru.murad.yourmarket.model;

import jakarta.persistence.*;
import lombok.*;
import ru.murad.yourmarket.model.enums.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "advertisement_drafts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AdvertisementDraft extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "telegram_user_id", nullable = false, unique = true)
    private Long telegramUserId;
    @Column(name = "chat_id", nullable = false)
    private Long chatId;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private AdvertisementCreationStep step;
    @Enumerated(EnumType.STRING)
    private AdvertisementCategory category;
    private String title;
    @Column(length = 2000)
    private String description;
    @Column(name = "item_price", precision = 11, scale = 2)
    private BigDecimal itemPrice;
    @Column(name = "telegram_file_id")
    private String telegramFileId;
    private String city;
    @Column(name = "region_code", length = 40) private String regionCode;
    @Column(name = "region_name_snapshot", length = 120) private String regionNameSnapshot;
    @Column(name = "city_code", length = 60) private String cityCode;
    @Column(name = "city_name_snapshot", length = 120) private String cityNameSnapshot;
    @Column(name = "custom_locality", length = 100) private String customLocality;
    private String contact;
    @Column(name = "edit_mode", nullable = false)
    private boolean editMode;
}
