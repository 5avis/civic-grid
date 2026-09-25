package com.example.civicgrid.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "street_lights")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StreetLight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pole_code", nullable = false, unique = true)
    private String poleCode;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    @Builder.Default
    @Column(name = "dimming_percentage", nullable = false)
    private Integer dimmingPercentage = 0;

    @Builder.Default
    @Column(name = "simulated_power_draw", nullable = false)
    private Double simulatedPowerDraw = 0.0;

    @Builder.Default
    @Column(nullable = false, length = 30)
    private String status = "ACTIVE";
}
