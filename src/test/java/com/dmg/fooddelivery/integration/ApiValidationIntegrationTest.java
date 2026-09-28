package com.dmg.fooddelivery.integration;

import com.dmg.fooddelivery.model.Role;
import com.dmg.fooddelivery.model.User;
import com.dmg.fooddelivery.repository.UserRepository;
import com.dmg.fooddelivery.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiValidationIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwtService;

    @Test
    void publicStatusAndBrowsingRemainAccessible() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(jsonPath("status").value("running"));
        mvc.perform(get("/api/cities")).andExpect(status().isOk());
        mvc.perform(get("/api/restaurants")).andExpect(status().isOk());
    }

    @Test
    void anonymousWritesAndPrivateReadsAreRejected() throws Exception {
        mvc.perform(post("/api/admin/cities").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Unauthorized\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/mine")).andExpect(status().isForbidden());
        mvc.perform(get("/api/restaurants/1/orders")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customersCannotPerformOwnerOrAdminActions() throws Exception {
        mvc.perform(post("/api/admin/cities").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Unauthorized\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/restaurants").contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void nullOrderItemsAreValidationErrors() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"restaurantId\":1,\"items\":[null]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("message").value("Validation failed"));
    }

    @Test
    void malformedJsonAndParametersAreClientErrors() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{invalid")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/restaurants?cityId=invalid")).andExpect(status().isBadRequest());
    }

    @Test
    void disabledUsersCannotUsePreviouslyIssuedTokens() throws Exception {
        User user = users.save(User.builder().name("Disabled")
                .email("disabled-" + System.nanoTime() + "@test.com").passwordHash("unused")
                .role(Role.ADMIN).active(false).build());
        String token = jwtService.generateToken(user.getId(), user.getEmail(), user.getRole().name());
        mvc.perform(post("/api/admin/cities").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Unauthorized\"}"))
                .andExpect(status().isForbidden());
    }
}
