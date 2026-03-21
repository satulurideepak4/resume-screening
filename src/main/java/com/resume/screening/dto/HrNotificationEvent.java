package com.resume.screening.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Published to RabbitMQ when a candidate scores above the threshold.
 * Consumed by EmailConsumerService to send HR notification.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrNotificationEvent {

    private UUID applicationId;
    private UUID candidateId;
    private String candidateFullName;
    private String candidateEmail;
    private String candidatePhone;
    private String candidateRole;
    private String totalExperience;
    private String jobTitle;
    private UUID jobPostingId;
    private double overallScore;
    private int jdAlignmentPercent;
    private int experiencePercent;
    private int technicalDepthPercent;
    private String scoringReason;
    private long triggeredAt;           // epoch millis
}