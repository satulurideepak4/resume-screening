package com.resume.screening.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "job_postings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;         // full JD text used for AI scoring

    private String requiredExperience;  // e.g. "4+ years"

    private String location;

    private boolean active = true;

    @CreationTimestamp
    private LocalDateTime createdAt;
}