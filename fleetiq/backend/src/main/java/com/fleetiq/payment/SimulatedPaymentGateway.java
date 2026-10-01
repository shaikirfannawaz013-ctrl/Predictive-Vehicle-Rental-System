package com.fleetiq.payment;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Development gateway: always creates an order; declines a configurable share of payments. */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private final double failureRate;

    public SimulatedPaymentGateway(@Value("${app.payment.simulated-failure-rate:0.0}") double failureRate) {
        this.failureRate = failureRate;
    }

    @Override
    public String createOrder(String reference, BigDecimal amount) {
        return "order_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
    }

    @Override
    public boolean verify(String gatewayOrderId, String gatewayPaymentId, String signature) {
        return ThreadLocalRandom.current().nextDouble() >= failureRate;
    }
}
