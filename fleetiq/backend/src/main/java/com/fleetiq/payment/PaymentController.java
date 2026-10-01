package com.fleetiq.payment;

import com.fleetiq.payment.PaymentDtos.ConfirmPaymentRequest;
import com.fleetiq.payment.PaymentDtos.CreatePaymentRequest;
import com.fleetiq.payment.PaymentDtos.PaymentResponse;
import com.fleetiq.user.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse create(@Valid @RequestBody CreatePaymentRequest req, @AuthenticationPrincipal AuthUser user) {
        return payments.create(user, req);
    }

    @PostMapping("/{paymentId}/confirm")
    public PaymentResponse confirm(@PathVariable String paymentId,
                                   @RequestBody(required = false) ConfirmPaymentRequest req,
                                   @AuthenticationPrincipal AuthUser user) {
        return payments.confirm(user, paymentId, req);
    }
}
