package com.fleetiq.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record CreatePaymentRequest(@NotNull Long bookingId, @NotBlank String method, String upiId) {}

    /** Fields a real gateway returns to the browser after checkout; unused by the simulator. */
    public record ConfirmPaymentRequest(String gatewayPaymentId, String signature) {}

    public record PaymentResponse(String paymentId, Long bookingId, PaymentState status, BigDecimal amount,
                                  String gatewayOrderId) {
        static PaymentResponse from(Payment p) {
            return new PaymentResponse(p.getReference(), p.getBooking().getId(), p.getStatus(), p.getAmount(),
                    p.getGatewayOrderId());
        }
    }
}
