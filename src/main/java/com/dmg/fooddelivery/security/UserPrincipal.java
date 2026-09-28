package com.dmg.fooddelivery.security;

import com.dmg.fooddelivery.model.User;
import org.springframework.lang.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class UserPrincipal implements UserDetails {

    @NonNull
    private final User user;

    public UserPrincipal(@NonNull User user) {
        this.user = Objects.requireNonNull(user, "user must not be null");
    }

    public Long getId() {
        return user.getId();
    }

    @NonNull
    public User getUser() {
        return user;
    }

    @Override
    @NonNull
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Objects.requireNonNull(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }
}
