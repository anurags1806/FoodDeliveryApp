package com.dmg.fooddelivery.security;

import com.dmg.fooddelivery.model.User;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {}

    @NonNull
    public static User currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        }
        return principal.getUser();
    }

    @NonNull
    public static Long currentUserId() {
        Long id = currentUser().getId();
        if (id == null) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated user has no ID");
        }
        return id;
    }
}
