package com.resume.screening.service;

import com.resume.screening.dto.ApplicationDashboardDto;
import com.resume.screening.entity.Application;
import com.resume.screening.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final ApplicationRepository applicationRepository;
    private final CacheService cacheService;

    /**
     * Returns a paginated, filtered list of applications for the HR dashboard.
     * Strategy: fetch IDs + scores from PostgreSQL (sorted), then try Redis for
     * full DTO per record, falling back to mapping from the JPA entity.
     */
    public Page<ApplicationDashboardDto> getApplications(
            UUID jobPostingId,
            Double minScore,
            Application.ApplicationStatus status,
            Pageable pageable) {

        Page<Application> page = (jobPostingId != null)
                ? applicationRepository.findByJobPostingIdWithFilters(jobPostingId, minScore, status, pageable)
                : applicationRepository.findAllWithFilters(minScore, status, pageable);

        List<ApplicationDashboardDto> dtos = page.getContent().stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    /**
     * Single application detail — Redis-first, PostgreSQL fallback.
     */
    public ApplicationDashboardDto getApplication(UUID applicationId) {
        // Try Redis cache first
        ApplicationDashboardDto cached = cacheService.getCachedApplication(applicationId);
        if (cached != null) {
            log.debug("Cache hit for application {}", applicationId);
            return cached;
        }

        log.debug("Cache miss for application {}, reading from PostgreSQL", applicationId);
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NoSuchElementException("Application not found: " + applicationId));
        return toDto(application);
    }

    /**
     * Summary stats for a job posting (total, shortlisted, review, low-match).
     */
    public Map<String, Long> getJobStats(UUID jobPostingId) {
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("total",       applicationRepository.countByJobPostingId(jobPostingId));
        stats.put("shortlisted", applicationRepository.countByJobPostingIdAndStatus(
                jobPostingId, Application.ApplicationStatus.SHORTLISTED));
        stats.put("review",      applicationRepository.countByJobPostingIdAndStatus(
                jobPostingId, Application.ApplicationStatus.REVIEW));
        stats.put("lowMatch",    applicationRepository.countByJobPostingIdAndStatus(
                jobPostingId, Application.ApplicationStatus.LOW_MATCH));
        return stats;
    }

    // Maps JPA entity → DTO (used on cache miss)
    private ApplicationDashboardDto toDto(Application app) {
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
                .jobTitle(app.getJobPosting().getTitle())
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