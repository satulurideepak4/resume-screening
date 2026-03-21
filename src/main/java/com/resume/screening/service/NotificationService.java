package com.resume.screening.service;

import com.resume.screening.dto.HrNotificationEvent;
import com.resume.screening.dto.ResumeSubmittedEvent;
import com.resume.screening.dto.ScoringResult;
import com.resume.screening.entity.Application;
import com.resume.screening.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final RabbitTemplate rabbitTemplate;
    private final ApplicationRepository applicationRepository;

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.routing-key.hr-notification}")
    private String hrRoutingKey;

    @Value("${app.scoring.threshold}")
    private double threshold;

    /**
     * Called after scoring. If score > threshold, publishes an HrNotificationEvent
     * to RabbitMQ and marks the application as HR-notified.
     */
    public void notifyIfEligible(Application application,
                                 ResumeSubmittedEvent event,
                                 ScoringResult result) {
        if (result.getOverallScore() <= threshold) {
            log.debug("Score {:.1f} did not exceed threshold {:.1f} for application {}. No HR notification.",
                    result.getOverallScore(), threshold, application.getId());
            return;
        }

        log.info("Score {:.1f} exceeds threshold {:.1f}. Publishing HR notification for application {}",
                result.getOverallScore(), threshold, application.getId());

        HrNotificationEvent notificationEvent = HrNotificationEvent.builder()
                .applicationId(application.getId())
                .candidateId(application.getCandidate().getId())
                .candidateFullName(application.getCandidate().getFullName())
                .candidateEmail(application.getCandidate().getEmail())
                .candidatePhone(application.getCandidate().getPhone())
                .candidateRole(application.getCandidate().getCandidateRole())
                .totalExperience(application.getCandidate().getTotalExperience())
                .jobTitle(event.getJobTitle())
                .jobPostingId(application.getJobPosting().getId())
                .overallScore(result.getOverallScore())
                .jdAlignmentPercent(result.getJdAlignmentPercent())
                .experiencePercent(result.getExperiencePercent())
                .technicalDepthPercent(result.getTechnicalDepthPercent())
                .scoringReason(result.getReason())
                .triggeredAt(System.currentTimeMillis())
                .build();

        try {
            rabbitTemplate.convertAndSend(exchange, hrRoutingKey, notificationEvent);

            // Mark as notified in DB so we don't send duplicate emails on retry
            application.setHrNotified(true);
            applicationRepository.save(application);

            log.info("HR notification published and application {} marked as notified.", application.getId());
        } catch (Exception e) {
            log.error("Failed to publish HR notification for application {}", application.getId(), e);
            // Don't rethrow — notification failure should not fail the scoring transaction
        }
    }
}