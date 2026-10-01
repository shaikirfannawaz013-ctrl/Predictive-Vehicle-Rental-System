package com.fleetiq.booking;

import com.fleetiq.booking.BookingDtos.BookingResponse;
import com.fleetiq.booking.BookingDtos.CreateBookingRequest;
import com.fleetiq.booking.BookingDtos.LateReturnResponse;
import com.fleetiq.booking.BookingDtos.ReturnVehicleRequest;
import com.fleetiq.common.ApiException;
import com.fleetiq.common.EventPublisher;
import com.fleetiq.common.Money;
import com.fleetiq.config.AppProperties;
import com.fleetiq.demand.DemandMessages.BookingEventMessage;
import com.fleetiq.fleet.Vehicle;
import com.fleetiq.fleet.VehicleRepository;
import com.fleetiq.fleet.VehicleStatus;
import com.fleetiq.notification.NotificationService;
import com.fleetiq.notification.NotificationType;
import com.fleetiq.payment.PaymentRepository;
import com.fleetiq.pricing.PricingService;
import com.fleetiq.pricing.PricingService.Quote;
import com.fleetiq.risk.RiskService;
import com.fleetiq.user.AuthUser;
import com.fleetiq.user.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final BookingRepository bookings;
    private final VehicleRepository vehicles;
    private final UserRepository users;
    private final PaymentRepository payments;
    private final PricingService pricing;
    private final NotificationService notifications;
    private final RiskService risk;
    private final EventPublisher events;
    private final AppProperties props;

    public BookingService(BookingRepository bookings, VehicleRepository vehicles, UserRepository users,
                          PaymentRepository payments, PricingService pricing, NotificationService notifications,
                          RiskService risk, EventPublisher events, AppProperties props) {
        this.bookings = bookings;
        this.vehicles = vehicles;
        this.users = users;
        this.payments = payments;
        this.pricing = pricing;
        this.notifications = notifications;
        this.risk = risk;
        this.events = events;
        this.props = props;
    }

    /**
     * Creates a booking in PENDING_PAYMENT with the quoted price held for app.pricing.hold-minutes.
     * The vehicle row is locked (SELECT ... FOR UPDATE) while checking for overlaps, so two
     * simultaneous requests for the same car and dates can't both succeed.
     */
    @Transactional
    public BookingResponse create(AuthUser user, CreateBookingRequest req) {
        PricingService.validateRange(req.startTime(), req.endTime());
        Vehicle vehicle = vehicles.findByIdForUpdate(req.vehicleId())
                .orElseThrow(() -> ApiException.notFound("Vehicle not found."));
        if (vehicle.getStatus() == VehicleStatus.MAINTENANCE) {
            throw ApiException.conflict("This vehicle is being serviced. Choose another vehicle.");
        }
        Instant now = Instant.now();
        long overlaps = bookings.countOverlaps(vehicle.getId(), req.startTime(), req.endTime(),
                BookingStatus.OCCUPYING, BookingStatus.PENDING_PAYMENT, now);
        if (overlaps > 0) {
            throw ApiException.conflict("This vehicle was just booked for those dates. Pick another vehicle or change your dates.");
        }

        Quote q = pricing.quote(vehicle, req.startTime(), req.endTime(), user.id());
        Booking b = new Booking();
        b.setCustomer(users.getReferenceById(user.id()));
        b.setVehicle(vehicle);
        b.setStartTime(req.startTime());
        b.setEndTime(req.endTime());
        b.setDays(q.days());
        b.setBaseRate(q.baseRate());
        b.setMultiplier(BigDecimal.valueOf(q.multiplier()));
        b.setSubtotal(q.subtotal());
        b.setTax(q.tax());
        b.setTotal(q.total());
        b.setDeposit(q.deposit());
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        b.setPaymentStatus(PaymentStatus.PENDING);
        b.setHoldExpiresAt(now.plus(Duration.ofMinutes(props.pricing().holdMinutes())));
        bookings.save(b);
        return BookingResponse.from(b);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> mine(AuthUser user) {
        return bookings.findForCustomer(user.id()).stream().map(BookingResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public BookingResponse get(AuthUser user, Long id) {
        return BookingResponse.from(loadVisible(user, id));
    }

    @Transactional
    public BookingResponse cancel(AuthUser user, Long id) {
        Booking b = loadVisible(user, id);
        if (b.getStatus() != BookingStatus.PENDING_PAYMENT && b.getStatus() != BookingStatus.CONFIRMED) {
            throw ApiException.conflict("Only upcoming bookings can be cancelled.");
        }
        b.setStatus(BookingStatus.CANCELLED);
        b.setHoldExpiresAt(null);
        if (b.getPaymentStatus() == PaymentStatus.PAID) {
            b.setPaymentStatus(PaymentStatus.REFUNDED);
            payments.markRefunded(b.getId());
            notifications.notifyUser(b.getCustomer().getId(), NotificationType.PAYMENT,
                    "Booking #" + b.getId() + " cancelled. Your refund of " + money(b.getTotal())
                            + " will reach you in 5–7 working days.");
        }
        publish("BOOKING_CANCELLED", b);
        return BookingResponse.from(b);
    }

    /** Fleet manager records the vehicle coming back. Computes the late fee and frees the vehicle. */
    @Transactional
    public BookingResponse returnVehicle(Long id, ReturnVehicleRequest req) {
        Booking b = bookings.findDetailed(id).orElseThrow(() -> ApiException.notFound("Booking not found."));
        if (b.getStatus() != BookingStatus.ACTIVE) {
            throw ApiException.conflict("Only active bookings can be returned.");
        }
        Instant now = Instant.now();
        long lateHours = billableLateHours(b.getEndTime(), now);
        b.setActualReturnTime(now);
        b.setStatus(BookingStatus.COMPLETED);
        b.setReturnedLate(lateHours > 0);
        b.setDamageReported(req != null && req.damageReported());
        b.setLateFee(props.pricing().lateFeePerHour().multiply(BigDecimal.valueOf(lateHours)));

        Vehicle v = b.getVehicle();
        v.setStatus(VehicleStatus.AVAILABLE);
        if (req != null && req.odometerKm() != null && req.odometerKm() > v.getOdometerKm()) {
            v.setOdometerKm(req.odometerKm());
        }
        if (lateHours > 0) {
            notifications.notifyUser(b.getCustomer().getId(), NotificationType.LATE_RETURN,
                    "You returned " + v.getName() + " " + lateHours + " h late. A late fee of "
                            + money(b.getLateFee()) + " has been charged.");
        }
        risk.invalidate();
        publish("VEHICLE_RETURNED", b);
        return BookingResponse.from(b);
    }

    @Transactional(readOnly = true)
    public List<LateReturnResponse> lateReturns() {
        Instant now = Instant.now();
        return bookings.findByStatusEndingBefore(BookingStatus.ACTIVE, now).stream()
                .map(b -> {
                    long hours = billableLateHours(b.getEndTime(), now);
                    long overdue = Math.max(1, Duration.between(b.getEndTime(), now).toHours());
                    return new LateReturnResponse(b.getId(), b.getVehicle().getId(), b.getVehicle().getName(),
                            b.getCustomer().getName(), b.getCustomer().getPhone(), b.getEndTime(), overdue,
                            props.pricing().lateFeePerHour().multiply(BigDecimal.valueOf(hours)));
                })
                .toList();
    }

    /** Hours charged: rounded up, after the grace period. */
    long billableLateHours(Instant due, Instant returnedAt) {
        Instant chargeableFrom = due.plus(Duration.ofMinutes(props.pricing().graceMinutes()));
        if (!returnedAt.isAfter(chargeableFrom)) {
            return 0;
        }
        return (long) Math.ceil(Duration.between(due, returnedAt).toMinutes() / 60.0);
    }

    void publish(String event, Booking b) {
        events.publish(props.topics().bookingEvents(), String.valueOf(b.getVehicle().getId()),
                new BookingEventMessage(event, b.getId(), b.getVehicle().getId(), b.getVehicle().getZone().getId(),
                        b.getVehicle().getType().name(), Instant.now()));
    }

    private Booking loadVisible(AuthUser user, Long id) {
        Booking b = bookings.findDetailed(id).orElseThrow(() -> ApiException.notFound("Booking not found."));
        if (!user.isAdmin() && !b.isOwnedBy(user.id())) {
            throw ApiException.notFound("Booking not found.");
        }
        return b;
    }

    static String money(BigDecimal v) {
        return "₹" + Money.rupees(v).toPlainString();
    }
}
