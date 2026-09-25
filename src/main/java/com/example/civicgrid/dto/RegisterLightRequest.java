package com.example.civicgrid.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterLightRequest {

    @NotBlank(message = "Pole code is required.")
    private String poleCode;

    @NotNull(message = "Zone ID is required.")
    private Long zoneId;

    @Min(value = 0, message = "Dimming percentage must be at least 0.")
    @Max(value = 100, message = "Dimming percentage must be at most 100.")
    private Integer dimmingPercentage;
}
