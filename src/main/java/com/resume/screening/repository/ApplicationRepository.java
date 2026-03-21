package com.resume.screening.repository;

import com.resume.screening.entity.Application;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    Optional<Application> findByCandidateIdAndJobPostingId(UUID candidateId, UUID jobPostingId);

    // Dashboard: all applications for a job, ordered by score descending
    @Query("""
        SELECT a FROM Application a
        JOIN FETCH a.candidate c
        JOIN FETCH a.jobPosting j
        WHERE j.id = :jobPostingId
          AND (:minScore IS NULL OR a.overallScore >= :minScore)
          AND (:status IS NULL OR a.status = :status)
        ORDER BY a.overallScore DESC NULLS LAST
    """)
    Page<Application> findByJobPostingIdWithFilters(
            @Param("jobPostingId") UUID jobPostingId,
            @Param("minScore") Double minScore,
            @Param("status") Application.ApplicationStatus status,
            Pageable pageable
    );

    // All applications across all jobs (admin view)
    @Query("""
        SELECT a FROM Application a
        JOIN FETCH a.candidate c
        JOIN FETCH a.jobPosting j
        WHERE (:minScore IS NULL OR a.overallScore >= :minScore)
          AND (:status IS NULL OR a.status = :status)
        ORDER BY a.overallScore DESC NULLS LAST
    """)
    Page<Application> findAllWithFilters(
            @Param("minScore") Double minScore,
            @Param("status") Application.ApplicationStatus status,
            Pageable pageable
    );

    List<Application> findByHrNotifiedFalseAndOverallScoreGreaterThan(double threshold);

    long countByJobPostingId(UUID jobPostingId);

    long countByJobPostingIdAndStatus(UUID jobPostingId, Application.ApplicationStatus status);
}