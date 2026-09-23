package com.dmg.fooddelivery.integration;

import com.dmg.fooddelivery.model.User;
import com.dmg.fooddelivery.security.UserPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * SecurityContextHolder defaults to a ThreadLocal strategy, so each thread
 * in a concurrency test must set its own authentication before calling
 * into a service that reads SecurityUtils.currentUser().
 */
public final class TestAuth {

    private TestAuth() {}

    public static void loginAs(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        var token = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(token);
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
