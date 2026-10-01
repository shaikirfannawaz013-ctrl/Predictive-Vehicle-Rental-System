package com.fleetiq.user;

import com.fleetiq.common.ApiException;
import com.fleetiq.config.JwtService;
import com.fleetiq.user.AuthDtos.AuthResponse;
import com.fleetiq.user.AuthDtos.LoginRequest;
import com.fleetiq.user.AuthDtos.RegisterRequest;
import com.fleetiq.user.AuthDtos.UserResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        User user = users.findByEmailIgnoreCase(req.email())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect."));
        return tokenFor(user.toAuthUser());
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("An account with this email already exists. Sign in instead.");
        }
        User user = users.save(new User(req.name().trim(), req.email().trim().toLowerCase(), req.phone(),
                req.licenseNumber().trim().toUpperCase(), encoder.encode(req.password()), Role.CUSTOMER));
        return tokenFor(user.toAuthUser());
    }

    private AuthResponse tokenFor(AuthUser user) {
        return new AuthResponse(jwt.issue(user), UserResponse.from(user));
    }
}
