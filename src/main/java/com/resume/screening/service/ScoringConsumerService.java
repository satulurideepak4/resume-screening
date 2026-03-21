package com.resume.screening.service;

import com.resume.screening.dto.ResumeSubmittedEvent;
import com.resume.screening.dto.ScoringResult;
import com.resume.screening.entity.Application;
import com.resume.screening.repository.ApplicationRepository;
import com.resume.screening.scoring.AiScoringEngine;
import com.resume.screening.service.CacheService;
import com.resume.screening.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScoringConsumerService {

    private final ApplicationRepository applicationRepository;
    private final AiScoringEngine aiScoringEngine;
    private final CacheService cacheService;
    private final NotificationService notificationService;

    /**
     * Consumes resume-submissions topic.
     * For each event:
     *   1. Call AI scoring engine
     *   2. Persist score to PostgreSQL
     *   3. Cache score in Redis
     *   4. Trigger HR notification if score > threshold
     */
    @KafkaListener(
            topics = "${app.kafka.topic.resume-submitted}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    @Transactional
    public void consume(@Payload ResumeSubmittedEvent event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
                        @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Consumed ResumeSubmittedEvent: applicationId={} partition={} offset={}",
                event.getApplicationId(), partition, offset);

        // Fetch the persisted application
        Application application = applicationRepository.findById(event.getApplicationId())
                .orElse(null);

        if (application == null) {
            log.error("Application not found in DB for event applicationId={}. Skipping.",
                    event.getApplicationId());
            return;
        }

        // Skip if already scored (idempotency guard — handles redelivery)
        if (application.getScoringStatus() == Application.ScoringStatus.SCORED) {
            log.warn("Application {} already scored. Skipping duplicate event.", event.getApplicationId());
            return;
        }

        try {
            // 1. Score with AI
            log.info("Scoring resume for candidate={} job={}",
                    event.getCandidateEmail(), event.getJobTitle());
            ScoringResult result = aiScoringEngine.score(
                    event.getJobDescription(),
                    event.getResumeText(),
                    event.getJobTitle()
            );

            if (!result.isSuccess()) {
                markFailed(application, "AI scoring returned failure: " + result.getErrorMessage());
                return;
            }

            // 2. Persist score to PostgreSQL
            persistScore(application, result);
            log.info("Persisted score {}/10 for application={}", result.getOverallScore(), application.getId());

            // 3. Cache in Redis
            cacheService.cacheApplicationScore(application, event);
            log.debug("Cached score for application={}", application.getId());

            // 4. Notify HR if score exceeds threshold
            notificationService.notifyIfEligible(application, event, result);

        } catch (Exception e) {
            log.error("Error processing application={}", event.getApplicationId(), e);
            markFailed(application, e.getMessage());
            // Do NOT rethrow — we don't want Kafka to redeliver a fundamentally broken record
        }
    }

    private void persistScore(Application application, ScoringResult result) {
        application.setOverallScore(result.getOverallScore());
        application.setJdAlignmentPercent(result.getJdAlignmentPercent());
        application.setExperiencePercent(result.getExperiencePercent());
        application.setTechnicalDepthPercent(result.getTechnicalDepthPercent());
        application.setScoringReason(result.getReason());
        application.setScoringStatus(Application.ScoringStatus.SCORED);
        application.setScoredAt(LocalDateTime.now());
        application.setStatus(deriveStatus(result.getOverallScore()));
        applicationRepository.save(application);
    }

    private void markFailed(Application application, String reason) {
        application.setScoringStatus(Application.ScoringStatus.FAILED);
        application.setScoringReason("SCORING FAILED: " + reason);
        applicationRepository.save(application);
        log.error("Marked application {} as FAILED: {}", application.getId(), reason);
    }

    private Application.ApplicationStatus deriveStatus(double score) {
        if (score > 7.0) return Application.ApplicationStatus.SHORTLISTED;
        if (score >= 5.0) return Application.ApplicationStatus.REVIEW;
        return Application.ApplicationStatus.LOW_MATCH;
    }
}