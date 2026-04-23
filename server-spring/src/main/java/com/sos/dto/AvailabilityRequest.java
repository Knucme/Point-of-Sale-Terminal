package com.sos.dto;

import jakarta.validation.constraints.NotBlank;

public class AvailabilityRequest {

    @NotBlank(message = "availabilityStatus is required.")
    private String availabilityStatus;

    public AvailabilityRequest() {}

    public String getAvailabilityStatus() { return availabilityStatus; }
    public void setAvailabilityStatus(String availabilityStatus) { this.availabilityStatus = availabilityStatus; }
}
