package ru.murad.yourmarket.repository; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository; import ru.murad.yourmarket.model.VehicleDetails;
public interface VehicleDetailsRepository extends JpaRepository<VehicleDetails,UUID>{ Optional<VehicleDetails> findByAdvertisementId(UUID id); }
