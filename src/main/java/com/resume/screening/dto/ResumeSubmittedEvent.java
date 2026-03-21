package com.resume.screening.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Kafka message published to topic: resume-submissions
 * Consumed by the ScoringConsumerService.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeSubmittedEvent {

    private UUID applicationId;
    private UUID candidateId;
    private UUID jobPostingId;
    private String candidateEmail;
    private String candidateFullName;
    private String resumeText;          // full parsed PDF text
    private String jobDescription;      // full JD text (de-normalised for consumer convenience)
    private String jobTitle;
    private long submittedAt;           // epoch millis
}