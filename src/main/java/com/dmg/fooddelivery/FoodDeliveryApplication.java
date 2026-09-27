package com.dmg.fooddelivery;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FoodDeliveryApplication {
    public static void main(String[] args) {
        System.out.println("Food Delivery Application is starting...");
        SpringApplication.run(FoodDeliveryApplication.class, args);
    }
}
