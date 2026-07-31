package ru.murad.yourmarket.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.murad.yourmarket.model.AdvertisementDraftPhoto;
import java.util.*;
public interface AdvertisementDraftPhotoRepository extends JpaRepository<AdvertisementDraftPhoto,UUID>{
 List<AdvertisementDraftPhoto> findByDraftIdOrderByPosition(UUID draftId);
 long countByDraftId(UUID draftId);
 void deleteByDraftId(UUID draftId);
}
