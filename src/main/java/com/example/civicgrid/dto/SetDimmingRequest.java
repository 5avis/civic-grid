package com.example.civicgrid.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetDimmingRequest {

    @NotNull(message = "Dimming percentage is required.")
    @Min(value = 0, message = "Dimming percentage must be at least 0.")
    @Max(value = 100, message = "Dimming percentage must be at most 100.")
    private Integer dimmingPercentage;
}
