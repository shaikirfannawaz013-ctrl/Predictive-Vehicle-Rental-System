package com.fleetiq.booking;

import com.fleetiq.booking.BookingDtos.BookingResponse;
import com.fleetiq.booking.BookingDtos.CreateBookingRequest;
import com.fleetiq.booking.BookingDtos.LateReturnResponse;
import com.fleetiq.booking.BookingDtos.ReturnVehicleRequest;
import com.fleetiq.user.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookings;

    public BookingController(BookingService bookings) {
        this.bookings = bookings;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BookingResponse create(@Valid @RequestBody CreateBookingRequest req, @AuthenticationPrincipal AuthUser user) {
        return bookings.create(user, req);
    }

    @GetMapping("/me")
    public List<BookingResponse> mine(@AuthenticationPrincipal AuthUser user) {
        return bookings.mine(user);
    }

    @GetMapping("/late")
    @PreAuthorize("hasRole('ADMIN')")
    public List<LateReturnResponse> late() {
        return bookings.lateReturns();
    }

    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return bookings.get(user, id);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id, @AuthenticationPrincipal AuthUser user) {
        return bookings.cancel(user, id);
    }

    @PostMapping("/{id}/return")
    @PreAuthorize("hasRole('ADMIN')")
    public BookingResponse returnVehicle(@PathVariable Long id,
                                         @Valid @RequestBody(required = false) ReturnVehicleRequest req) {
        return bookings.returnVehicle(id, req);
    }
}
