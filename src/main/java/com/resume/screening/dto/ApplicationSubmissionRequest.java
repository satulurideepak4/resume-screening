package com.resume.screening.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class ApplicationSubmissionRequest {

    @NotNull(message = "Job posting ID is required")
    private UUID jobPostingId;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String phone;

    private String location;

    private String totalExperience;

    private String currentRole;

    private String education;

    private String noticePeriod;

    private String expectedCtc;
}