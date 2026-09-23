package com.dmg.fooddelivery.config;

import com.dmg.fooddelivery.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/h2-console/**", "/actuator/health").permitAll()

                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                .requestMatchers(HttpMethod.POST, "/api/restaurants").hasAnyRole("ADMIN", "RESTAURANT_OWNER")
                .requestMatchers(HttpMethod.POST, "/api/restaurants/*/menu-items").hasRole("RESTAURANT_OWNER")
                .requestMatchers(HttpMethod.PUT, "/api/menu-items/**").hasRole("RESTAURANT_OWNER")
                .requestMatchers(HttpMethod.DELETE, "/api/menu-items/**").hasRole("RESTAURANT_OWNER")
                // Specific sub-resource rule MUST be declared before the
                // broader public GET rule below, since Spring Security
                // uses first-match-wins over these ordered matchers.
                .requestMatchers(HttpMethod.GET, "/api/restaurants/*/orders").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                // Browsing cities/restaurants/menus is public (customers browse before login)
                .requestMatchers(HttpMethod.GET, "/api/cities/**", "/api/restaurants/**").permitAll()

                .requestMatchers(HttpMethod.POST, "/api/orders").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET, "/api/orders/mine").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.POST, "/api/orders/*/rating").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.PATCH, "/api/orders/*/status")
                        .hasAnyRole("ADMIN", "RESTAURANT_OWNER", "DELIVERY_PARTNER", "CUSTOMER")

                .requestMatchers("/api/delivery/**").hasRole("DELIVERY_PARTNER")

                .anyRequest().authenticated()
            )
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin())) // for h2-console
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
