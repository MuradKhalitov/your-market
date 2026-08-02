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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import ru.murad.yourmarket.model.Advertisement;
import ru.murad.yourmarket.model.AdvertisementPhoto;
import ru.murad.yourmarket.model.Payment;
import ru.murad.yourmarket.model.TelegramUser;
import ru.murad.yourmarket.model.AdvertisementDraft;
import ru.murad.yourmarket.model.enums.AdvertisementCategory;
import ru.murad.yourmarket.model.enums.AdvertisementStatus;
import ru.murad.yourmarket.model.enums.PaymentStatus;
import ru.murad.yourmarket.model.enums.AdvertisementCreationStep;
import ru.murad.yourmarket.model.enums.InvoiceSendStatus;
import ru.murad.yourmarket.repository.AdvertisementChannelMessageRepository;
import ru.murad.yourmarket.repository.AdvertisementPhotoRepository;
import ru.murad.yourmarket.repository.AdvertisementRepository;
import ru.murad.yourmarket.repository.PaymentRepository;
import ru.murad.yourmarket.service.AdvertisementExpirationService;
import ru.murad.yourmarket.service.AdvertisementPublicationService;
import ru.murad.yourmarket.service.RateLimitService;
import ru.murad.yourmarket.service.PaymentService;
import ru.murad.yourmarket.repository.TelegramUserRepository;
import ru.murad.yourmarket.repository.AdvertisementDraftRepository;
import ru.murad.yourmarket.telegram.TelegramGateway;

@SpringBootTest(properties = {
        "telegram.bot.token=123456:integration-test-token",
        "telegram.bot.username=test_bot",
        "telegram.channel.id=-1001234567890",
        "telegram.channel.username=test_channel",
        "telegram.channel.url=https://t.me/test_channel",
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
    JdbcTemplate jdbcTemplate;
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
    @Autowired
    PaymentService paymentService;
    @Autowired
    TelegramUserRepository telegramUsers;
    @Autowired
    AdvertisementDraftRepository drafts;
    @MockitoBean
    TelegramGateway telegramGateway;

    @Test
    void starsOnlyPaymentSchemaHasNoProviderColumn() {
        Integer providerColumnCount = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_name = 'payments' and column_name = 'provider_payment_charge_id'
                """, Integer.class);
        assertEquals(0, providerColumnCount);
    }

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

    @Test
    void concurrentInvoiceClaimsCreateOnePaymentAndOneOwner() throws Exception {
        long userId = 88001L;
        prepareCompleteDraft(userId);
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var task = (java.util.concurrent.Callable<PaymentService.InvoiceClaim>) () -> {
                start.await();
                return paymentService.createPaymentAndClaimInvoice(userId, "integration_user");
            };
            var first = executor.submit(task);
            var second = executor.submit(task);
            start.countDown();
            var claims = java.util.List.of(first.get(), second.get());

            assertEquals(1, claims.stream().filter(c -> c.result() == PaymentService.InvoiceClaimResult.CLAIMED).count());
            assertEquals(1, claims.stream().filter(c -> c.result() == PaymentService.InvoiceClaimResult.IN_PROGRESS).count());
            assertEquals(1, repository.findAll().stream().filter(p -> p.getTelegramUserId().equals(userId)).count());
            assertEquals(1, claims.stream().map(c -> c.payment().getPayload()).distinct().count());
        }
    }

    @Test
    void persistedSentInvoiceIsNotResetOrRecreatedAfterReload() {
        long userId = 88002L;
        prepareCompleteDraft(userId);
        PaymentService.InvoiceClaim claimed = paymentService.createPaymentAndClaimInvoice(userId, "integration_user");
        paymentService.markInvoiceSent(claimed.payment().getId(), claimed.operationId());
        String payload = claimed.payment().getPayload();

        PaymentService.InvoiceClaim repeated = paymentService.createPaymentAndClaimInvoice(userId, "integration_user");

        assertEquals(PaymentService.InvoiceClaimResult.ALREADY_SENT, repeated.result());
        assertEquals(InvoiceSendStatus.SENT, repeated.payment().getInvoiceSendStatus());
        assertEquals(payload, repeated.payment().getPayload());
        assertEquals(1, repository.findAll().stream().filter(p -> p.getTelegramUserId().equals(userId)).count());
    }

    @Test
    void confirmedInvoiceFailureIsOwnerCheckedInPostgres() {
        long userId = 88003L;
        prepareCompleteDraft(userId);
        PaymentService.InvoiceClaim claimed = paymentService.createPaymentAndClaimInvoice(userId, "integration_user");
        UUID owner = claimed.operationId();

        paymentService.failInvoiceSending(claimed.payment().getId(), UUID.randomUUID(), "foreign worker");
        Payment unchanged = repository.findById(claimed.payment().getId()).orElseThrow();
        assertEquals(InvoiceSendStatus.SENDING, unchanged.getInvoiceSendStatus());
        assertEquals(owner, unchanged.getInvoiceOperationId());

        paymentService.failInvoiceSending(claimed.payment().getId(), owner,
                "Telegram invoice rejected: CURRENCY_TOTAL_AMOUNT_INVALID");
        Payment released = repository.findById(claimed.payment().getId()).orElseThrow();
        assertEquals(InvoiceSendStatus.NOT_SENT, released.getInvoiceSendStatus());
        assertEquals(null, released.getInvoiceOperationId());
        assertEquals(null, released.getInvoiceSendingSince());
        assertEquals("Telegram invoice rejected: CURRENCY_TOTAL_AMOUNT_INVALID", released.getFailureReason());
    }

    @Test
    void schemaRejectsNonStarsCurrency() {
        long userId = 88004L;
        prepareCompleteDraft(userId);
        Advertisement pending = advertisements.saveAndFlush(Advertisement.builder().telegramUserId(userId)
                .chatId(userId).category(AdvertisementCategory.OTHER).title("Старое объявление")
                .description("Достаточно длинное описание").itemPrice(BigDecimal.TEN).telegramFileId("file")
                .city("Москва").contact("@seller").status(AdvertisementStatus.WAITING_FOR_PAYMENT).build());
        Payment invalidPayment = Payment.builder().advertisementId(pending.getId())
                .telegramUserId(userId).payload("invalid-currency-" + userId).amount(199)
                .currency("USD").status(PaymentStatus.CREATED).invoiceSendStatus(InvoiceSendStatus.NOT_SENT).build();

        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> repository.saveAndFlush(invalidPayment));
    }

    private void prepareCompleteDraft(long userId) {
        telegramUsers.saveAndFlush(TelegramUser.builder().telegramUserId(userId).chatId(userId)
                .username("integration_user").firstName("Test").build());
        drafts.saveAndFlush(AdvertisementDraft.builder().telegramUserId(userId).chatId(userId)
                .step(AdvertisementCreationStep.PREVIEW).category(AdvertisementCategory.OTHER)
                .title("Тестовый товар").description("Достаточно длинное описание")
                .itemPrice(BigDecimal.TEN).telegramFileId("file").city("Москва").contact("@seller")
                .build());
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
            .amount(1).currency("XTR").status(PaymentStatus.CREATED).build();
    }
}
