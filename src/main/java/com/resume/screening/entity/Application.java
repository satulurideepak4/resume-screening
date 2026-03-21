package com.resume.screening.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "applications",
        uniqueConstraints = @UniqueConstraint(columnNames = {"candidate_id", "job_posting_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    // AI scoring breakdown
    private Double overallScore;          // 0.0 – 10.0
    private Integer jdAlignmentPercent;   // 0 – 100
    private Integer experiencePercent;    // 0 – 100
    private Integer technicalDepthPercent;// 0 – 100

    @Column(columnDefinition = "TEXT")
    private String scoringReason;         // AI explanation

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status;

    private boolean hrNotified = false;

    @Enumerated(EnumType.STRING)
    private ScoringStatus scoringStatus;  // PENDING, SCORED, FAILED

    @CreationTimestamp
    private LocalDateTime appliedAt;

    private LocalDateTime scoredAt;

    public enum ApplicationStatus {
        SHORTLISTED,   // score > 7
        REVIEW,        // 5 <= score <= 7
        LOW_MATCH      // score < 5
    }

    public enum ScoringStatus {
        PENDING,
        SCORED,
        FAILED
    }
}