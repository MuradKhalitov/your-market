package ru.murad.yourmarket.model;

import jakarta.persistence.*;
import lombok.*;
import ru.murad.yourmarket.model.enums.PaymentStatus;
import ru.murad.yourmarket.model.enums.InvoiceSendStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Payment extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "advertisement_id", nullable = false, unique = true)
    private UUID advertisementId;
    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;
    @Column(nullable = false, unique = true)
    private String payload;
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;
    @Column(nullable = false, length = 3)
    private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private PaymentStatus status;
    @Column(name = "telegram_payment_charge_id", unique = true)
    private String telegramPaymentChargeId;
    @Column(name = "provider_payment_charge_id", unique = true)
    private String providerPaymentChargeId;
    @Column(name = "failure_reason")
    private String failureReason;
    @Column(name = "paid_at")
    private Instant paidAt;
    @Enumerated(EnumType.STRING) @Column(name = "invoice_send_status", nullable = false)
    @Builder.Default private InvoiceSendStatus invoiceSendStatus = InvoiceSendStatus.NOT_SENT;
    @Column(name = "invoice_sending_since")
    private Instant invoiceSendingSince;
    @Column(name = "invoice_sent_at")
    private Instant invoiceSentAt;
    @Column(name = "invoice_operation_id")
    private UUID invoiceOperationId;
}
