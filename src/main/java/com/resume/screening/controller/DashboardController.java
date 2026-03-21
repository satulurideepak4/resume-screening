package com.resume.screening.controller;

import com.resume.screening.dto.ApplicationDashboardDto;
import com.resume.screening.entity.Application;
import com.resume.screening.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * GET /api/dashboard/applications
     *
     * Query params:
     *   jobPostingId  (optional) UUID  — filter by job
     *   minScore      (optional) double — e.g. 7.0
     *   status        (optional) SHORTLISTED | REVIEW | LOW_MATCH
     *   page          (default 0)
     *   size          (default 20)
     *
     * Returns paginated list sorted by score DESC.
     */
    @GetMapping("/applications")
    public ResponseEntity<Page<ApplicationDashboardDto>> getApplications(
            @RequestParam(required = false) UUID jobPostingId,
            @RequestParam(required = false) Double minScore,
            @RequestParam(required = false) Application.ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<ApplicationDashboardDto> result =
                dashboardService.getApplications(jobPostingId, minScore, status, pageable);
        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/dashboard/applications/{applicationId}
     * Single application detail — Redis-first, DB fallback.
     */
    @GetMapping("/applications/{applicationId}")
    public ResponseEntity<ApplicationDashboardDto> getApplication(
            @PathVariable UUID applicationId) {
        return ResponseEntity.ok(dashboardService.getApplication(applicationId));
    }

    /**
     * GET /api/dashboard/jobs/{jobPostingId}/stats
     * Returns shortlist/review/low-match counts for a job posting.
     */
    @GetMapping("/jobs/{jobPostingId}/stats")
    public ResponseEntity<Map<String, Long>> getJobStats(@PathVariable UUID jobPostingId) {
        return ResponseEntity.ok(dashboardService.getJobStats(jobPostingId));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
    }
}