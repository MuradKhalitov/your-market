package ru.murad.yourmarket.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.*;
import ru.murad.yourmarket.model.enums.*;

@Entity
@Table(name = "vehicle_details")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleDetails extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "advertisement_id", nullable = false, unique = true)
    private UUID advertisementId;
    @Column(name = "brand_code", nullable = false)
    private String brandCode;
    @Column(name = "brand_name_snapshot", nullable = false)
    private String brandNameSnapshot;
    @Column(name = "custom_brand")
    private String customBrand;
    @Column(name = "model_code", nullable = false)
    private String modelCode;
    @Column(name = "model_name_snapshot", nullable = false)
    private String modelNameSnapshot;
    @Column(name = "custom_model")
    private String customModel;
    @Column(name = "production_year", nullable = false)
    private Integer productionYear;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransmissionType transmission;
    @Enumerated(EnumType.STRING)
    @Column(name = "engine_type", nullable = false)
    private EngineType engineType;
    @Column(name = "engine_volume_liters", precision = 3, scale = 1)
    private BigDecimal engineVolumeLiters;
    @Column(name = "mileage_km", nullable = false)
    private Integer mileageKm;
    @Enumerated(EnumType.STRING)
    @Column(name = "drive_type", nullable = false)
    private DriveType driveType;
}
