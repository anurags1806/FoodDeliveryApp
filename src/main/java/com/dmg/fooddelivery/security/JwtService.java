package com.dmg.fooddelivery.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@NonNull @Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(@NonNull Long userId, @NonNull String email, @NonNull String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(email)
                .claims(Map.of("userId", userId, "role", role))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    @Nullable
    public String extractEmail(@NonNull String token) {
        return extractAllClaims(token).getSubject();
    }

    @Nullable
    public Long extractUserId(@NonNull String token) {
        return extractAllClaims(token).get("userId", Long.class);
    }

    @Nullable
    public String extractRole(@NonNull String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public boolean isTokenValid(@Nullable String token) {
        if (token == null || token.isBlank()) return false;
        try {
            Claims claims = extractAllClaims(token);
            Date expiration = claims.getExpiration();
            String subject = claims.getSubject();
            return expiration != null && expiration.after(new Date())
                    && subject != null && !subject.isBlank();
        } catch (Exception e) {
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
