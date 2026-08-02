package ru.murad.yourmarket.repository; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository; import ru.murad.yourmarket.model.VehicleDraftDetails;
public interface VehicleDraftDetailsRepository extends JpaRepository<VehicleDraftDetails,UUID>{ Optional<VehicleDraftDetails> findByAdvertisementDraftId(UUID id); void deleteByAdvertisementDraftId(UUID id); }
