package ru.murad.yourmarket.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.murad.yourmarket.model.AdvertisementPhoto;
import java.util.*;
public interface AdvertisementPhotoRepository extends JpaRepository<AdvertisementPhoto,UUID>{
 List<AdvertisementPhoto> findByAdvertisementIdOrderByPosition(UUID advertisementId);
}
