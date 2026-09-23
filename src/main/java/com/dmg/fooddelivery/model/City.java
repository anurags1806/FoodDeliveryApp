package com.dmg.fooddelivery.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "city", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class City {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Builder.Default
    private boolean active = true;
}
