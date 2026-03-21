package com.resume.screening.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Parsed result from the AI scoring API call.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoringResult {

    private double overallScore;           // 0.0 – 10.0
    private int jdAlignmentPercent;        // 0 – 100
    private int experiencePercent;         // 0 – 100
    private int technicalDepthPercent;     // 0 – 100
    private String reason;                 // human-readable explanation from AI
    private boolean success;
    private String errorMessage;           // populated on failure
}