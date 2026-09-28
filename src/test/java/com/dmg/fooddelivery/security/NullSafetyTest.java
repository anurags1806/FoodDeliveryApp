package com.dmg.fooddelivery.security;

import com.dmg.fooddelivery.model.OrderStatus;
import com.dmg.fooddelivery.model.User;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class NullSafetyTest {
    private static final String SECRET = "test-only-secret-key-change-me-please-32bytesmin";
    private final JwtService jwt = new JwtService(SECRET, 60_000);

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingOrUnexpectedPrincipalProducesAuthenticationError() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(SecurityUtils::currentUser)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("anonymousUser", null, List.of()));
        assertThatThrownBy(SecurityUtils::currentUser)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void principalRequiresUserAndCurrentUserIdRequiresPersistedIdentity() {
        assertThatNullPointerException().isThrownBy(() -> new UserPrincipal(null));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new UserPrincipal(new User()), null, List.of()));
        assertThatThrownBy(SecurityUtils::currentUserId)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void nullIsNeverALegalStatusTransition() {
        for (OrderStatus status : OrderStatus.values()) {
            assertThat(status.canTransitionTo(null)).isFalse();
        }
    }

    @Test
    void tokenValidationRejectsMissingRequiredClaimsAndNullInput() {
        var key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        assertThat(jwt.isTokenValid(null)).isFalse();
        assertThat(jwt.isTokenValid(" ")).isFalse();
        assertThat(jwt.isTokenValid(Jwts.builder().subject("user@test.com").signWith(key).compact())).isFalse();
        assertThat(jwt.isTokenValid(Jwts.builder().expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(key).compact())).isFalse();
        assertThat(jwt.isTokenValid(jwt.generateToken(1L, "user@test.com", "CUSTOMER"))).isTrue();
    }
}
