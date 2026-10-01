package com.fleetiq.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record RegisterRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "[0-9]{10}", message = "must be a 10-digit mobile number") String phone,
            @NotBlank @Size(max = 40) String licenseNumber,
            @NotBlank @Size(min = 8, message = "must be at least 8 characters") String password) {}

    public record UserResponse(Long id, String name, String email, Role role) {
        static UserResponse from(AuthUser u) {
            return new UserResponse(u.id(), u.name(), u.email(), u.role());
        }
    }

    public record AuthResponse(String token, UserResponse user) {}
}
