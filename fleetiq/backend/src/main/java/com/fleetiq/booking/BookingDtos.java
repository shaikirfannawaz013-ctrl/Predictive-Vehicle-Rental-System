package com.fleetiq.booking;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;

public final class BookingDtos {
    private BookingDtos() {}

    public record CreateBookingRequest(@NotNull Long vehicleId, @NotNull Instant startTime, @NotNull Instant endTime) {}

    public record ReturnVehicleRequest(@Min(0) Integer odometerKm, boolean damageReported) {}

    public record BookingResponse(Long id, Long vehicleId, String vehicleName, String zoneName, String customer,
                                  Instant startTime, Instant endTime, Instant actualReturnTime, int days,
                                  double multiplier, BigDecimal subtotal, BigDecimal tax, BigDecimal total,
                                  BigDecimal deposit, BigDecimal lateFee, BookingStatus status,
                                  PaymentStatus paymentStatus, Instant holdExpiresAt) {
        public static BookingResponse from(Booking b) {
            return new BookingResponse(b.getId(), b.getVehicle().getId(), b.getVehicle().getName(),
                    b.getVehicle().getZone().getName(), b.getCustomer().getName(), b.getStartTime(), b.getEndTime(),
                    b.getActualReturnTime(), b.getDays(), b.getMultiplier().doubleValue(), b.getSubtotal(),
                    b.getTax(), b.getTotal(), b.getDeposit(), b.getLateFee(), b.getStatus(), b.getPaymentStatus(),
                    b.getHoldExpiresAt());
        }
    }

    public record LateReturnResponse(Long id, Long vehicleId, String vehicleName, String customer,
                                     String customerPhone, Instant endTime, long overdueHours, BigDecimal penalty) {}
}
