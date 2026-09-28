package com.dmg.fooddelivery.service;

import static java.util.Objects.requireNonNull;

import com.dmg.fooddelivery.dto.request.LoginRequest;
import com.dmg.fooddelivery.dto.request.RegisterRequest;
import com.dmg.fooddelivery.dto.response.AuthResponse;
import com.dmg.fooddelivery.exception.BadRequestException;
import com.dmg.fooddelivery.model.User;
import com.dmg.fooddelivery.repository.UserRepository;
import com.dmg.fooddelivery.security.JwtService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Validated
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public AuthResponse register(@NotNull @Valid RegisterRequest request) {
        if (userRepository.existsByEmail(requireNonNull(request.email()))) {
            throw new BadRequestException("Email already registered: " + requireNonNull(request.email()));
        }
        User user = User.builder()
                .name(request.name())
                .email(requireNonNull(request.email()))
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();
        user = userRepository.save(user);
        String token = jwtService.generateToken(requireNonNull(user.getId()), requireNonNull(user.getEmail()), requireNonNull(user.getRole().name()));
        return new AuthResponse(token, requireNonNull(user.getId()), user.getName(), requireNonNull(user.getEmail()), requireNonNull(user.getRole().name()));
    }

    public AuthResponse login(@NotNull @Valid LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(requireNonNull(request.email()), request.password()));
        } catch (BadCredentialsException e) {
            throw new BadRequestException("Invalid email or password");
        }
        User user = requireNonNull(userRepository.findByEmail(requireNonNull(request.email()))
                .orElseThrow(() -> new BadRequestException("Invalid email or password")));
        String token = jwtService.generateToken(requireNonNull(user.getId()), requireNonNull(user.getEmail()), requireNonNull(user.getRole().name()));
        return new AuthResponse(token, requireNonNull(user.getId()), user.getName(), requireNonNull(user.getEmail()), requireNonNull(user.getRole().name()));
    }
}
