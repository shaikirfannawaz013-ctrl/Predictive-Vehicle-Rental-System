package com.fleetiq.seed;

import com.fleetiq.booking.Booking;
import com.fleetiq.booking.BookingRepository;
import com.fleetiq.booking.BookingStatus;
import com.fleetiq.booking.PaymentStatus;
import com.fleetiq.common.Money;
import com.fleetiq.demand.DemandService;
import com.fleetiq.fleet.Vehicle;
import com.fleetiq.fleet.VehicleRepository;
import com.fleetiq.fleet.VehicleStatus;
import com.fleetiq.fleet.VehicleType;
import com.fleetiq.payment.Payment;
import com.fleetiq.payment.PaymentRepository;
import com.fleetiq.payment.PaymentState;
import com.fleetiq.user.Role;
import com.fleetiq.user.User;
import com.fleetiq.user.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Demo data: users, 30 days of rental history (feeds analytics, risk and maintenance), one overdue
 * booking, and a burst of recent searches in Redis so the Tirupati demand example is visible
 * immediately. Disable with SEED_DATA=false.
 *
 * Logins: admin@fleetiq.in / admin123 (fleet manager), demo@fleetiq.in / demo1234 (customer).
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    /** name, email, late-return probability, damage probability, payment-failure probability */
    private record Persona(String name, String email, double late, double damage, double payFail) {}

    private static final List<Persona> PERSONAS = List.of(
            new Persona("Ravi Kumar", "ravi@example.in", 0.05, 0.0, 0.0),
            new Persona("Anitha Reddy", "anitha@example.in", 0.30, 0.0, 0.05),
            new Persona("Suresh Babu", "suresh@example.in", 0.45, 0.10, 0.25),
            new Persona("Meena Iyer", "meena@example.in", 0.05, 0.25, 0.20),
            new Persona("Karthik Naidu", "karthik@example.in", 0.0, 0.0, 0.0),
            new Persona("Lakshmi Prasad", "lakshmi@example.in", 0.10, 0.0, 0.0));

    private static final Map<String, Integer> DEMO_SEARCHES =
            Map.of("TPT-C", 38, "TML", 24, "REN", 19, "SKH", 11, "CHG", 7, "PTR", 4);

    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final PasswordEncoder encoder;
    private final DemandService demand;
    private final TransactionTemplate tx;
    private final Random rnd = new Random(42);

    public DataSeeder(UserRepository users, VehicleRepository vehicles, BookingRepository bookings,
                      PaymentRepository payments, PasswordEncoder encoder, DemandService demand,
                      TransactionTemplate tx) {
        this.users = users;
        this.vehicles = vehicles;
        this.bookings = bookings;
        this.payments = payments;
        this.encoder = encoder;
        this.demand = demand;
        this.tx = tx;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() == 0) {
            tx.executeWithoutResult(status -> seedDatabase());
            log.info("Seeded demo users and rental history");
        }
        seedRecentSearches();
    }

    private void seedDatabase() {
        users.save(new User("Fleet Manager", "admin@fleetiq.in", "9000000001", "ADMIN",
                encoder.encode("admin123"), Role.ADMIN));
        users.save(new User("Demo Customer", "demo@fleetiq.in", "9000000002", "AP0320230001234",
                encoder.encode("demo1234"), Role.CUSTOMER));
        List<User> customers = new ArrayList<>();
        List<Persona> personas = new ArrayList<>();
        int n = 0;
        for (Persona p : PERSONAS) {
            customers.add(users.save(new User(p.name(), p.email(), "98480" + String.format("%05d", ++n),
                    "AP03" + (2019 + n) + "000" + (4000 + n), encoder.encode("password123"), Role.CUSTOMER)));
            personas.add(p);
        }

        Instant now = Instant.now();
        List<Vehicle> fleet = vehicles.findAll();
        Vehicle overdueVehicle = fleet.stream().filter(v -> v.getName().equals("Hyundai i20")).findFirst()
                .orElse(fleet.get(0));

        for (Vehicle v : fleet) {
            boolean isOverdueVehicle = v == overdueVehicle;
            Instant horizon = isOverdueVehicle ? now.minus(Duration.ofDays(4)) : now;
            Instant t = now.minus(Duration.ofDays(30)).plus(Duration.ofHours(rnd.nextInt(48)));
            while (t.isBefore(horizon)) {
                int idx = rnd.nextInt(customers.size());
                Instant end = t.plus(Duration.ofHours(12 + rnd.nextInt(60)));
                if (end.isAfter(horizon)) {
                    // Some vehicles are out on rent right now
                    if (!isOverdueVehicle && v.getStatus() == VehicleStatus.AVAILABLE && rnd.nextDouble() < 0.25) {
                        Booking active = bookings.save(booking(v, customers.get(idx), t,
                                now.plus(Duration.ofHours(6 + rnd.nextInt(40))), BookingStatus.ACTIVE, null, false));
                        payment(active, false, t.minus(Duration.ofHours(3)));
                        v.setStatus(VehicleStatus.RENTED);
                    }
                    break;
                }
                Persona p = personas.get(idx);
                boolean late = rnd.nextDouble() < p.late();
                Instant returned = late ? end.plus(Duration.ofMinutes(60 + rnd.nextInt(8 * 60)))
                        : end.minus(Duration.ofMinutes(rnd.nextInt(90)));
                Booking b = bookings.save(booking(v, customers.get(idx), t, end, BookingStatus.COMPLETED, returned,
                        rnd.nextDouble() < p.damage()));
                if (late) {
                    long hours = (long) Math.ceil(Duration.between(end, returned).toMinutes() / 60.0);
                    b.setReturnedLate(true);
                    b.setLateNotified(true);
                    b.setLateFee(BigDecimal.valueOf(hours * 250));
                }
                payment(b, rnd.nextDouble() < p.payFail(), t.minus(Duration.ofHours(2 + rnd.nextInt(48))));
                t = returned.plus(Duration.ofHours(4 + rnd.nextInt(60)));
            }
        }

        // The overdue booking the late-return detector will flag
        Booking overdue = bookings.save(booking(overdueVehicle, customers.get(1), now.minus(Duration.ofDays(3)),
                now.minus(Duration.ofHours(5)), BookingStatus.ACTIVE, null, false));
        payment(overdue, false, now.minus(Duration.ofDays(3)).minus(Duration.ofHours(1)));
        overdueVehicle.setStatus(VehicleStatus.RENTED);

        // Odometers reflect the seeded trips
        for (Vehicle v : fleet) {
            int km = v.getType() == VehicleType.BIKE ? 400 : 1500;
            v.setOdometerKm(v.getOdometerKm() + rnd.nextInt(km));
        }
    }

    private Booking booking(Vehicle v, User customer, Instant start, Instant end, BookingStatus status,
                            Instant returned, boolean damage) {
        int days = (int) Math.max(1, Math.ceil(Duration.between(start, end).toHours() / 24.0));
        double multiplier = Money.round2(1.0 + rnd.nextInt(45) / 100.0);
        BigDecimal subtotal = Money.times(v.getBaseRate().multiply(BigDecimal.valueOf(days)), multiplier);
        BigDecimal tax = Money.times(subtotal, 0.18);
        Booking b = new Booking();
        b.setCustomer(customer);
        b.setVehicle(v);
        b.setStartTime(start);
        b.setEndTime(end);
        b.setActualReturnTime(returned);
        b.setDays(days);
        b.setBaseRate(v.getBaseRate());
        b.setMultiplier(BigDecimal.valueOf(multiplier));
        b.setSubtotal(subtotal);
        b.setTax(tax);
        b.setTotal(subtotal.add(tax));
        b.setDeposit(BigDecimal.valueOf(2000));
        b.setStatus(status);
        b.setPaymentStatus(PaymentStatus.PAID);
        b.setDamageReported(damage);
        b.setCreatedAt(start.minus(Duration.ofHours(4)));
        return b;
    }

    /** A successful payment, optionally preceded by a declined attempt. */
    private void payment(Booking b, boolean failedFirst, Instant at) {
        if (failedFirst) {
            payments.save(payment(b, PaymentState.FAILED, at.minus(Duration.ofMinutes(5))));
        }
        payments.save(payment(b, PaymentState.SUCCESS, at));
    }

    private Payment payment(Booking b, PaymentState state, Instant at) {
        Payment p = new Payment();
        p.setReference("pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        p.setBooking(b);
        p.setAmount(b.getTotal());
        p.setMethod(rnd.nextBoolean() ? "UPI" : "CARD");
        p.setStatus(state);
        p.setGatewayOrderId("order_seed" + rnd.nextInt(1_000_000));
        p.setCreatedAt(at);
        return p;
    }

    /** Recent searches expire after an hour, so they are re-seeded on every start. */
    private void seedRecentSearches() {
        if (demand.searchesLastHour("TPT-C") > 0) {
            return;
        }
        long now = System.currentTimeMillis();
        DEMO_SEARCHES.forEach((zone, count) -> {
            for (int i = 0; i < count; i++) {
                String type = zone.equals("TPT-C") && i % 3 != 2 ? "SUV"
                        : VehicleType.values()[rnd.nextInt(VehicleType.values().length)].name();
                demand.recordSearch(zone, type, Instant.ofEpochMilli(now - rnd.nextInt(50 * 60 * 1000)));
            }
        });
        demand.invalidate();
        log.info("Seeded recent searches for the demand demo");
    }
}
