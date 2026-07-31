package ru.murad.yourmarket.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.murad.yourmarket.model.Advertisement;
import ru.murad.yourmarket.model.AdvertisementPhoto;
import ru.murad.yourmarket.model.Payment;
import ru.murad.yourmarket.model.enums.AdvertisementCategory;
import ru.murad.yourmarket.model.enums.AdvertisementStatus;
import ru.murad.yourmarket.model.enums.PaymentStatus;
import ru.murad.yourmarket.repository.AdvertisementChannelMessageRepository;
import ru.murad.yourmarket.repository.AdvertisementPhotoRepository;
import ru.murad.yourmarket.repository.AdvertisementRepository;
import ru.murad.yourmarket.repository.PaymentRepository;
import ru.murad.yourmarket.service.AdvertisementExpirationService;
import ru.murad.yourmarket.service.AdvertisementPublicationService;
import ru.murad.yourmarket.service.RateLimitService;
import ru.murad.yourmarket.telegram.TelegramGateway;

@SpringBootTest(properties = {
        "telegram.bot.token=123456:integration-test-token",
        "telegram.bot.username=test_bot",
        "telegram.channel.id=-1001234567890",
        "telegram.channel.username=test_channel",
        "telegram.channel.url=https://t.me/test_channel",
        "telegram.payment.provider-token=test-provider-token",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.autoconfigure.exclude=org.telegram.telegrambots.longpolling.starter.TelegramBotStarterConfiguration"
})
@Testcontainers(disabledWithoutDocker = true)
class LiquibasePostgresIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    PaymentRepository repository;
    @Autowired
    AdvertisementPhotoRepository photos;
    @Autowired
    RateLimitService rateLimit;
    @Autowired
    AdvertisementRepository advertisements;
    @Autowired
    AdvertisementChannelMessageRepository channelMessages;
    @Autowired
    AdvertisementPublicationService publicationService;
    @Autowired
    AdvertisementExpirationService expirationService;
    @MockitoBean
    TelegramGateway telegramGateway;

    @Test
    void liquibaseAppliedAndPayloadConstraintIsEnforced() {
        repository.saveAndFlush(payment(UUID.randomUUID(), "same-payload"));
        assertThrows(DataIntegrityViolationException.class,
            () -> repository.saveAndFlush(payment(UUID.randomUUID(), "same-payload")));
    }

    @Test
    void photoPositionConstraintIsEnforced() {
        UUID advertisementId = UUID.randomUUID();
        photos.saveAndFlush(
            AdvertisementPhoto.builder().advertisementId(advertisementId).telegramFileId("a")
                .position(0).build());
        assertThrows(DataIntegrityViolationException.class, () -> photos.saveAndFlush(
            AdvertisementPhoto.builder().advertisementId(advertisementId).telegramFileId("b")
                .position(0).build()));
    }

    @Test
    void parallelRateLimitIsAtomic() throws Exception {
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.stream.IntStream.range(0, 30)
                .mapToObj(i -> (java.util.concurrent.Callable<Boolean>)
                    () -> rateLimit.allow(999L, "PARALLEL")).toList();
            long allowed = executor.invokeAll(tasks).stream().filter(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).count();
            assertEquals(20, allowed);
        }
    }

    @Test
    void concurrentPublicationProducesOneTelegramPost() throws Exception {
        Advertisement ad = advertisements.saveAndFlush(advertisement(AdvertisementStatus.PAID));
        Payment succeeded = payment(ad.getId(), "publication-" + ad.getId());
        succeeded.setStatus(PaymentStatus.SUCCEEDED);
        repository.saveAndFlush(succeeded);
        when(telegramGateway.publishAdvertisementPrimaryMessages(any())).thenReturn(
            java.util.List.of(77));
        runConcurrently(() -> publicationService.publish(ad.getId()));
        verify(telegramGateway, times(1)).publishAdvertisementPrimaryMessages(any());
        assertEquals(AdvertisementStatus.PUBLISHED,
            advertisements.findById(ad.getId()).orElseThrow().getStatus());
    }

    @Test
    void concurrentExpirationDeletesOnce() throws Exception {
        Advertisement ad = advertisement(AdvertisementStatus.PUBLISHED);
        ad.setChannelMessageId(88);
        ad.setPublishedAt(Instant.now().minusSeconds(100));
        ad.setExpiresAt(Instant.now().minusSeconds(1));
        ad = advertisements.saveAndFlush(ad);
        UUID id = ad.getId();
        channelMessages.saveAndFlush(ru.murad.yourmarket.model.AdvertisementChannelMessage.builder()
            .advertisementId(id).channelMessageId(88).position(0).build());
        runConcurrently(() -> expirationService.expire(id));
        verify(telegramGateway, times(1)).deleteChannelMessage(88);
        assertEquals(AdvertisementStatus.EXPIRED,
            advertisements.findById(id).orElseThrow().getStatus());
    }

    @Test
    void channelMessagePositionConstraintAllowsZeroToFiveOnly() {
        UUID ad = UUID.randomUUID();
        channelMessages.saveAndFlush(
            ru.murad.yourmarket.model.AdvertisementChannelMessage.builder().advertisementId(ad)
                .channelMessageId(1).position(0).build());
        channelMessages.saveAndFlush(
            ru.murad.yourmarket.model.AdvertisementChannelMessage.builder().advertisementId(ad)
                .channelMessageId(2).position(5).build());
        assertThrows(DataIntegrityViolationException.class, () -> channelMessages.saveAndFlush(
            ru.murad.yourmarket.model.AdvertisementChannelMessage.builder()
                .advertisementId(UUID.randomUUID()).channelMessageId(3).position(-1).build()));
        assertThrows(DataIntegrityViolationException.class, () -> channelMessages.saveAndFlush(
            ru.murad.yourmarket.model.AdvertisementChannelMessage.builder()
                .advertisementId(UUID.randomUUID()).channelMessageId(4).position(6).build()));
    }

    private void runConcurrently(Runnable action) throws Exception {
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var tasks = java.util.List.of((java.util.concurrent.Callable<Void>) () -> {
                    start.await();
                    action.run();
                    return null;
                },
                (java.util.concurrent.Callable<Void>) () -> {
                    start.await();
                    action.run();
                    return null;
                });
            var futures = tasks.stream().map(executor::submit).toList();
            start.countDown();
            for (var future : futures) {
                future.get();
            }
        }
    }

    private Advertisement advertisement(AdvertisementStatus status) {
        return Advertisement.builder().telegramUserId(55L).chatId(66L)
            .category(AdvertisementCategory.OTHER).title("Тестовый товар")
            .description("Достаточно длинное описание")
            .itemPrice(BigDecimal.TEN).telegramFileId("file").city("Москва").contact("@seller")
            .status(status).paidAt(Instant.now()).build();
    }

    private Payment payment(UUID advertisementId, String payload) {
        return Payment.builder().advertisementId(advertisementId).telegramUserId(1L)
            .payload(payload)
            .amount(new BigDecimal("199.00")).currency("RUB").status(PaymentStatus.CREATED).build();
    }
}
