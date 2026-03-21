package com.resume.screening.controller;

import com.resume.screening.entity.JobPosting;
import com.resume.screening.repository.JobPostingRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobPostingController {

    private final JobPostingRepository jobPostingRepository;

    @GetMapping
    public ResponseEntity<List<JobPosting>> getActiveJobs() {
        return ResponseEntity.ok(jobPostingRepository.findByActiveTrue());
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobPosting> getJob(@PathVariable UUID id) {
        return jobPostingRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<JobPosting> createJob(@Valid @RequestBody CreateJobRequest request) {
        JobPosting posting = JobPosting.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .requiredExperience(request.getRequiredExperience())
                .location(request.getLocation())
                .active(true)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(jobPostingRepository.save(posting));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        jobPostingRepository.findById(id).ifPresent(job -> {
            job.setActive(false);
            jobPostingRepository.save(job);
        });
        return ResponseEntity.noContent().build();
    }

    @Data
    static class CreateJobRequest {
        @NotBlank private String title;
        @NotBlank private String description;
        private String requiredExperience;
        private String location;
    }
}