package com.fleetiq.user;

/** The signed-in user, rebuilt from the JWT on every request (no DB lookup needed). */
public record AuthUser(Long id, String email, String name, Role role) {
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }
}
