package ru.murad.yourmarket.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;
import ru.murad.yourmarket.model.enums.*;

@Entity
@Table(name = "vehicle_draft_details")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleDraftDetails extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "advertisement_draft_id", nullable = false, unique = true)
    private UUID advertisementDraftId;
    @Column(name = "brand_code")
    private String brandCode;
    @Column(name = "brand_name_snapshot")
    private String brandNameSnapshot;
    @Column(name = "custom_brand")
    private String customBrand;
    @Column(name = "model_code")
    private String modelCode;
    @Column(name = "model_name_snapshot")
    private String modelNameSnapshot;
    @Column(name = "custom_model")
    private String customModel;
    @Column(name = "production_year")
    private Integer productionYear;
    @Enumerated(EnumType.STRING)
    private TransmissionType transmission;
    @Enumerated(EnumType.STRING)
    @Column(name = "engine_type")
    private EngineType engineType;
    @Column(name = "engine_volume_liters", precision = 3, scale = 1)
    private BigDecimal engineVolumeLiters;
    @Column(name = "mileage_km")
    private Integer mileageKm;
    @Enumerated(EnumType.STRING)
    @Column(name = "drive_type")
    private DriveType driveType;
}
