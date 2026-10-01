package com.fleetiq.payment;

import java.math.BigDecimal;

/**
 * Payment provider abstraction. Swap SimulatedPaymentGateway for a Razorpay implementation:
 * createOrder -> POST /v1/orders; verify -> HMAC-SHA256(orderId|paymentId, keySecret) == signature.
 */
public interface PaymentGateway {

    String createOrder(String reference, BigDecimal amount);

    boolean verify(String gatewayOrderId, String gatewayPaymentId, String signature);
}
