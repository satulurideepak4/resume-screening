package com.resume.screening.service;

import com.resume.screening.dto.ApplicationSubmissionRequest;
import com.resume.screening.dto.ResumeSubmittedEvent;
import com.resume.screening.entity.Application;
import com.resume.screening.entity.Candidate;
import com.resume.screening.entity.JobPosting;
import com.resume.screening.repository.ApplicationRepository;
import com.resume.screening.repository.CandidateRepository;
import com.resume.screening.repository.JobPostingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResumeProcessService {

    private final CandidateRepository candidateRepository;
    private final JobPostingRepository jobPostingRepository;
    private final ApplicationRepository applicationRepository;
    private final PdfParsingService pdfParsingService;
    private final KafkaTemplate<String, ResumeSubmittedEvent> kafkaTemplate;

    @Value("${app.kafka.topic.resume-submitted}")
    private String resumeSubmittedTopic;

    @Transactional
    public UUID processApplication(ApplicationSubmissionRequest request, MultipartFile resumeFile) {

        // 1. Validate job posting
        JobPosting jobPosting = jobPostingRepository.findById(request.getJobPostingId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Job posting not found: " + request.getJobPostingId()));

        if (!jobPosting.isActive()) {
            throw new IllegalStateException("Job posting is no longer active");
        }

        // 2. Parse resume PDF
        String resumeText = pdfParsingService.extractText(resumeFile);
        log.debug("Parsed resume for {} ({} chars)", request.getEmail(), resumeText.length());

        // 3. Upsert candidate (same email = update details, not a duplicate error)
        Candidate candidate = candidateRepository.findByEmail(request.getEmail())
                .map(existing -> updateCandidate(existing, request, resumeText, resumeFile.getOriginalFilename()))
                .orElseGet(() -> createCandidate(request, resumeText, resumeFile.getOriginalFilename()));
        candidate = candidateRepository.save(candidate);

        // 4. Check for duplicate application
        boolean alreadyApplied = applicationRepository
                .findByCandidateIdAndJobPostingId(candidate.getId(), jobPosting.getId())
                .isPresent();
        if (alreadyApplied) {
            throw new IllegalStateException(
                    "Candidate " + request.getEmail() + " has already applied for this position");
        }

        // 5. Create application in PENDING state
        Application application = Application.builder()
                .candidate(candidate)
                .jobPosting(jobPosting)
                .scoringStatus(Application.ScoringStatus.PENDING)
                .hrNotified(false)
                .build();
        application = applicationRepository.save(application);
        log.info("Created application {} for candidate {} → job {}",
                application.getId(), candidate.getEmail(), jobPosting.getTitle());

        // 6. Publish Kafka event (async, outside transaction commit is fine here
        //    because the DB record is already saved before we publish)
        publishToKafka(application, candidate, jobPosting, resumeText);

        return application.getId();
    }

    private void publishToKafka(Application application, Candidate candidate,
                                JobPosting jobPosting, String resumeText) {
        ResumeSubmittedEvent event = ResumeSubmittedEvent.builder()
                .applicationId(application.getId())
                .candidateId(candidate.getId())
                .jobPostingId(jobPosting.getId())
                .candidateEmail(candidate.getEmail())
                .candidateFullName(candidate.getFullName())
                .resumeText(resumeText)
                .jobDescription(jobPosting.getDescription())
                .jobTitle(jobPosting.getTitle())
                .submittedAt(System.currentTimeMillis())
                .build();

        // Use candidateId as Kafka key → same candidate always routes to same partition
        String partitionKey = candidate.getId().toString();

        CompletableFuture<SendResult<String, ResumeSubmittedEvent>> future =
                kafkaTemplate.send(resumeSubmittedTopic, partitionKey, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish ResumeSubmittedEvent for application {}",
                        application.getId(), ex);
            } else {
                log.info("Published ResumeSubmittedEvent for application {} to partition {}",
                        application.getId(),
                        result.getRecordMetadata().partition());
            }
        });
    }

    private Candidate createCandidate(ApplicationSubmissionRequest req,
                                      String resumeText, String fileName) {
        return Candidate.builder()
                .fullName(req.getFullName())
                .email(req.getEmail())
                .phone(req.getPhone())
                .location(req.getLocation())
                .totalExperience(req.getTotalExperience())
                .currentRole(req.getCurrentRole())
                .education(req.getEducation())
                .noticePeriod(req.getNoticePeriod())
                .expectedCtc(req.getExpectedCtc())
                .resumeText(resumeText)
                .resumeFileName(fileName)
                .build();
    }

    private Candidate updateCandidate(Candidate existing, ApplicationSubmissionRequest req,
                                      String resumeText, String fileName) {
        existing.setFullName(req.getFullName());
        existing.setPhone(req.getPhone());
        existing.setLocation(req.getLocation());
        existing.setTotalExperience(req.getTotalExperience());
        existing.setCurrentRole(req.getCurrentRole());
        existing.setEducation(req.getEducation());
        existing.setNoticePeriod(req.getNoticePeriod());
        existing.setExpectedCtc(req.getExpectedCtc());
        existing.setResumeText(resumeText);
        existing.setResumeFileName(fileName);
        return existing;
    }
}