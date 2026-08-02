package ru.murad.yourmarket.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.murad.yourmarket.dto.response.AdvertisementResponseDto;
import ru.murad.yourmarket.service.PublicationRetryService;
import ru.murad.yourmarket.service.PublicationTransactionService;
import ru.murad.yourmarket.service.PaymentService;
import ru.murad.yourmarket.service.PaymentRefundService;
import ru.murad.yourmarket.service.ModerationTransactionService;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/advertisements")
@RequiredArgsConstructor
public class AdminController {
    private final PublicationRetryService publicationService;
    private final PublicationTransactionService publicationTransactions;
    private final PaymentService paymentService;
    private final PaymentRefundService paymentRefundService;
    private final ModerationTransactionService moderationTransactions;

    @PostMapping("/{id}/retry-publication")
    public ResponseEntity<AdvertisementResponseDto> retryPublication(@PathVariable UUID id) {
        return ResponseEntity.ok(publicationService.retryAsAdmin(id));
    }

    @PostMapping("/{id}/resolve-publication")
    public ResponseEntity<AdvertisementResponseDto> resolvePublication(@PathVariable UUID id,
            @RequestParam PublicationTransactionService.Resolution action,
            @RequestParam(required = false) Integer channelMessageId) {
        return ResponseEntity.ok(publicationTransactions.resolve(id, action, channelMessageId));
    }

    @PostMapping("/payments/{paymentId}/resolve-invoice")
    public ResponseEntity<Void> resolveInvoice(@PathVariable UUID paymentId,@RequestParam boolean retryAllowed){paymentService.resolveInvoice(paymentId,retryAllowed);return ResponseEntity.noContent().build();}

    @PostMapping("/payments/{paymentId}/resolve-refund")
    public ResponseEntity<Void> resolveRefund(@PathVariable UUID paymentId,
            @RequestParam boolean refundConfirmed) {
        paymentRefundService.resolveRefund(paymentId, refundConfirmed);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/resolve-moderation")
    public ResponseEntity<Void> resolveModeration(@PathVariable UUID id,
            @RequestParam UUID operationId,
            @RequestParam boolean moderationMessageConfirmed,
            @RequestParam(required = false) Integer moderationMessageId) {
        moderationTransactions.resolveSubmission(id, operationId, moderationMessageConfirmed, moderationMessageId);
        return ResponseEntity.noContent().build();
    }
}
