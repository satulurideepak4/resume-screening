package com.resume.screening.repository;

import com.resume.screening.entity.JobPosting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobPostingRepository extends JpaRepository<JobPosting, UUID> {

    List<JobPosting> findByActiveTrue();
}