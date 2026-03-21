package com.resume.screening.dto;

import com.resume.screening.entity.Application;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationDashboardDto {

    private UUID applicationId;
    private UUID candidateId;
    private String candidateFullName;
    private String candidateEmail;
    private String candidatePhone;
    private String currentRole;
    private String totalExperience;
    private String education;
    private String location;
    private String noticePeriod;
    private String expectedCtc;

    private UUID jobPostingId;
    private String jobTitle;

    private Double overallScore;
    private Integer jdAlignmentPercent;
    private Integer experiencePercent;
    private Integer technicalDepthPercent;
    private String scoringReason;

    private Application.ApplicationStatus status;
    private Application.ScoringStatus scoringStatus;
    private boolean hrNotified;

    private LocalDateTime appliedAt;
    private LocalDateTime scoredAt;
}