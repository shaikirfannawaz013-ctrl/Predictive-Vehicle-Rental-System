package com.fleetiq.payment;

import com.fleetiq.booking.Booking;
import com.fleetiq.booking.BookingRepository;
import com.fleetiq.booking.BookingStatus;
import com.fleetiq.booking.PaymentStatus;
import com.fleetiq.common.ApiException;
import com.fleetiq.common.EventPublisher;
import com.fleetiq.common.Money;
import com.fleetiq.config.AppProperties;
import com.fleetiq.demand.DemandMessages.BookingEventMessage;
import com.fleetiq.notification.NotificationService;
import com.fleetiq.notification.NotificationType;
import com.fleetiq.payment.PaymentDtos.ConfirmPaymentRequest;
import com.fleetiq.payment.PaymentDtos.CreatePaymentRequest;
import com.fleetiq.payment.PaymentDtos.PaymentResponse;
import com.fleetiq.user.AuthUser;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

    private static final Set<String> METHODS = Set.of("UPI", "CARD", "NETBANKING");

    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final PaymentGateway gateway;
    private final NotificationService notifications;
    private final EventPublisher events;
    private final AppProperties props;

    public PaymentService(PaymentRepository payments, BookingRepository bookings, PaymentGateway gateway,
                          NotificationService notifications, EventPublisher events, AppProperties props) {
        this.payments = payments;
        this.bookings = bookings;
        this.gateway = gateway;
        this.notifications = notifications;
        this.events = events;
        this.props = props;
    }

    @Transactional
    public PaymentResponse create(AuthUser user, CreatePaymentRequest req) {
        String method = req.method().toUpperCase();
        if (!METHODS.contains(method)) {
            throw ApiException.badRequest("Choose UPI, card or net banking.");
        }
        Booking b = bookings.findDetailed(req.bookingId())
                .filter(x -> x.isOwnedBy(user.id()))
                .orElseThrow(() -> ApiException.notFound("Booking not found."));
        requirePayable(b);

        Payment p = new Payment();
        p.setReference("pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        p.setBooking(b);
        p.setAmount(b.getTotal());
        p.setMethod(method);
        p.setStatus(PaymentState.CREATED);
        p.setGatewayOrderId(gateway.createOrder(p.getReference(), b.getTotal()));
        payments.save(p);
        return PaymentResponse.from(p);
    }

    @Transactional
    public PaymentResponse confirm(AuthUser user, String reference, ConfirmPaymentRequest req) {
        Payment p = payments.findByReferenceDetailed(reference)
                .filter(x -> x.getBooking().isOwnedBy(user.id()))
                .orElseThrow(() -> ApiException.notFound("Payment not found."));
        if (p.getStatus() != PaymentState.CREATED) {
            return PaymentResponse.from(p); // idempotent: confirming twice is harmless
        }
        Booking b = p.getBooking();
        requirePayable(b);

        boolean ok = gateway.verify(p.getGatewayOrderId(),
                req == null ? null : req.gatewayPaymentId(), req == null ? null : req.signature());
        if (!ok) {
            p.setStatus(PaymentState.FAILED);
            return PaymentResponse.from(p);
        }
        p.setStatus(PaymentState.SUCCESS);
        b.setStatus(BookingStatus.CONFIRMED);
        b.setPaymentStatus(PaymentStatus.PAID);
        b.setHoldExpiresAt(null);

        notifications.notifyUser(b.getCustomer().getId(), NotificationType.PAYMENT,
                "Payment of ₹" + Money.rupees(p.getAmount()).toPlainString() + " received. Booking #" + b.getId()
                        + " for " + b.getVehicle().getName() + " is confirmed.");
        notifications.notifyAdmins(NotificationType.BOOKING,
                "New booking #" + b.getId() + ": " + b.getVehicle().getName() + " from "
                        + b.getVehicle().getZone().getName() + ".");
        events.publish(props.topics().bookingEvents(), String.valueOf(b.getVehicle().getId()),
                new BookingEventMessage("BOOKING_CONFIRMED", b.getId(), b.getVehicle().getId(),
                        b.getVehicle().getZone().getId(), b.getVehicle().getType().name(), Instant.now()));
        return PaymentResponse.from(p);
    }

    private static void requirePayable(Booking b) {
        if (b.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw ApiException.conflict("This booking isn't waiting for payment.");
        }
        if (b.getHoldExpiresAt() == null || b.getHoldExpiresAt().isBefore(Instant.now())) {
            throw ApiException.conflict("Your price hold expired. Search again to get a new price.");
        }
    }
}
