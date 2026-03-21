package com.resume.screening.controller;

import com.resume.screening.dto.ApplicationSubmissionRequest;
import com.resume.screening.service.ResumeProcessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {

    private final ResumeProcessService resumeProcessService;

    /**
     * POST /api/applications
     *
     * Accepts multipart/form-data with:
     *   - application  : JSON part (ApplicationSubmissionRequest)
     *   - resume       : PDF file
     *
     * Returns the created applicationId immediately.
     * Scoring happens asynchronously via Kafka.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> submitApplication(
            @RequestPart("application") @Valid ApplicationSubmissionRequest request,
            @RequestPart("resume") MultipartFile resumeFile) {

        log.info("Received application from {} for job {}", request.getEmail(), request.getJobPostingId());

        UUID applicationId = resumeProcessService.processApplication(request, resumeFile);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "applicationId", applicationId,
                "status", "PENDING",
                "message", "Your application has been received and is being reviewed. " +
                        "You will be contacted if shortlisted."
        ));
    }

    // Global exception handlers for this controller
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> handleConflict(IllegalStateException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
    }
}