package com.resume.screening.service;

import com.resume.screening.config.RedisConfig;
import com.resume.screening.dto.ApplicationDashboardDto;
import com.resume.screening.dto.ResumeSubmittedEvent;
import com.resume.screening.entity.Application;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisConfig redisConfig;

    // Key patterns
    // score:{applicationId}           → Double
    // application:{applicationId}     → ApplicationDashboardDto
    // job-stats:{jobPostingId}        → Map of counts

    private static final String SCORE_KEY_PREFIX       = "score:";
    private static final String APPLICATION_KEY_PREFIX = "application:";

    /**
     * Caches the overall score and full application DTO after scoring.
     */
    public void cacheApplicationScore(Application application, ResumeSubmittedEvent event) {
        try {
            String scoreKey = SCORE_KEY_PREFIX + application.getId();
            redisTemplate.opsForValue().set(
                    scoreKey,
                    application.getOverallScore(),
                    Duration.ofHours(redisConfig.scoreTtlHours)
            );

            ApplicationDashboardDto dto = buildDto(application, event);
            String appKey = APPLICATION_KEY_PREFIX + application.getId();
            redisTemplate.opsForValue().set(
                    appKey,
                    dto,
                    Duration.ofHours(redisConfig.candidateTtlHours)
            );

        } catch (Exception e) {
            // Redis failure should never block the main scoring flow
            log.error("Redis cache write failed for application {}: {}", application.getId(), e.getMessage());
        }
    }

    /**
     * Reads a cached ApplicationDashboardDto. Returns null on miss.
     */
    public ApplicationDashboardDto getCachedApplication(UUID applicationId) {
        try {
            Object value = redisTemplate.opsForValue().get(APPLICATION_KEY_PREFIX + applicationId);
            if (value instanceof ApplicationDashboardDto dto) return dto;
        } catch (Exception e) {
            log.warn("Redis cache read failed for application {}: {}", applicationId, e.getMessage());
        }
        return null;
    }

    /**
     * Evicts cached data for an application (e.g. after manual status update).
     */
    public void evict(UUID applicationId) {
        try {
            redisTemplate.delete(SCORE_KEY_PREFIX + applicationId);
            redisTemplate.delete(APPLICATION_KEY_PREFIX + applicationId);
        } catch (Exception e) {
            log.warn("Redis eviction failed for application {}: {}", applicationId, e.getMessage());
        }
    }

    private ApplicationDashboardDto buildDto(Application app, ResumeSubmittedEvent event) {
        return ApplicationDashboardDto.builder()
                .applicationId(app.getId())
                .candidateId(app.getCandidate().getId())
                .candidateFullName(app.getCandidate().getFullName())
                .candidateEmail(app.getCandidate().getEmail())
                .candidatePhone(app.getCandidate().getPhone())
                .candidateRole(app.getCandidate().getCandidateRole())
                .totalExperience(app.getCandidate().getTotalExperience())
                .education(app.getCandidate().getEducation())
                .location(app.getCandidate().getLocation())
                .noticePeriod(app.getCandidate().getNoticePeriod())
                .expectedCtc(app.getCandidate().getExpectedCtc())
                .jobPostingId(app.getJobPosting().getId())
                .jobTitle(event.getJobTitle())
                .overallScore(app.getOverallScore())
                .jdAlignmentPercent(app.getJdAlignmentPercent())
                .experiencePercent(app.getExperiencePercent())
                .technicalDepthPercent(app.getTechnicalDepthPercent())
                .scoringReason(app.getScoringReason())
                .status(app.getStatus())
                .scoringStatus(app.getScoringStatus())
                .hrNotified(app.isHrNotified())
                .appliedAt(app.getAppliedAt())
                .scoredAt(app.getScoredAt())
                .build();
    }
}