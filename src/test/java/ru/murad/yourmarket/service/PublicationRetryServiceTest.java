package ru.murad.yourmarket.service;
import org.junit.jupiter.api.*;
import ru.murad.yourmarket.exception.*;
import ru.murad.yourmarket.model.*;
import ru.murad.yourmarket.model.enums.*;
import ru.murad.yourmarket.repository.*;
import ru.murad.yourmarket.service.impl.PublicationRetryServiceImpl;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PublicationRetryServiceTest {
 AdvertisementRepository ads=mock(AdvertisementRepository.class); PaymentRepository payments=mock(PaymentRepository.class);
 TelegramUserRepository users=mock(TelegramUserRepository.class); AdvertisementPublicationService publication=mock(AdvertisementPublicationService.class);
 PublicationRetryServiceImpl service=new PublicationRetryServiceImpl(ads,payments,users,publication); UUID id=UUID.randomUUID();
 @Test void successfulRetry(){stub(1L,AdvertisementStatus.PUBLICATION_FAILED,PaymentStatus.SUCCEEDED,false);service.retryForUser(id,1L);verify(publication).publish(id);}
 @Test void foreignAdvertisement(){stub(2L,AdvertisementStatus.PUBLICATION_FAILED,PaymentStatus.SUCCEEDED,false);assertThrows(AdvertisementNotFoundException.class,()->service.retryForUser(id,1L));}
 @Test void unpaidAdvertisement(){stub(1L,AdvertisementStatus.PUBLICATION_FAILED,PaymentStatus.CREATED,false);assertThrows(InvalidPaymentStateException.class,()->service.retryForUser(id,1L));}
 @Test void publishedRepeatIsIdempotent(){stub(1L,AdvertisementStatus.PUBLISHED,PaymentStatus.SUCCEEDED,false);service.retryForUser(id,1L);verify(publication).publish(id);}
 private void stub(long owner,AdvertisementStatus status,PaymentStatus pay,boolean blocked){
  when(ads.findById(id)).thenReturn(Optional.of(Advertisement.builder().id(id).telegramUserId(owner).status(status).build()));
  when(payments.findByAdvertisementId(id)).thenReturn(Optional.of(Payment.builder().status(pay).build()));
  when(users.findByTelegramUserId(owner)).thenReturn(Optional.of(TelegramUser.builder().telegramUserId(owner).blocked(blocked).build()));
 }
}
