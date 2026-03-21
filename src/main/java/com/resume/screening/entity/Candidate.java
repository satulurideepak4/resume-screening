package com.resume.screening.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "candidates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    private String location;

    private String totalExperience;   // e.g. "3 years"

    private String candidateRole;

    private String education;

    private String noticePeriod;

    private String expectedCtc;

    @Column(columnDefinition = "TEXT")
    private String resumeText;        // parsed PDF text stored for re-scoring

    private String resumeFileName;

    @CreationTimestamp
    private LocalDateTime createdAt;
}