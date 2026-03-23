# Resume Screening Pipeline

> AI-powered resume screening built with Java Spring Boot. Candidates submit a form and PDF resume, the pipeline scores them against the job description using **Google Gemini AI (free)**, notifies HR by email for high scorers, and provides a dashboard API to filter and rank all applicants.

---

## Pipeline flow

<div align="center">
  <img src="assets/resume-screening.svg" alt="RemoteScope demo" width="900"/>
</div>
---

## Why each technology

| Technology | Role | Why |
|---|---|---|
| **Kafka** | Resume submission queue | Absorbs burst traffic. AI scoring takes 2-5s per resume — Kafka buffers submissions so ingestion stays instant. 6 partitions = 6 parallel workers. |
| **RabbitMQ** | HR notification queue | Email delivery can fail. RabbitMQ auto-requeues on failure. Only fires for score > 7 candidates so volume is low — Kafka would be overkill here. |
| **Redis** | Score cache | HR dashboard reads on every page load. Redis serves cached scores in under 1ms. Cache-aside pattern: Redis first, PostgreSQL fallback on miss. |
| **PostgreSQL** | Persistent storage | Source of truth for candidates, jobs, scores. Supports `text[]` array for `requiredSkills` passed directly to the AI prompt. |
| **Gemini 1.5 Flash** | AI scoring | Completely free (1M tokens/day, no credit card). Native `responseMimeType: application/json` forces pure JSON output — no markdown fences, guaranteed parseable. |
| **PDFBox** | PDF parsing | Extracts resume text before Kafka publish. Trimmed to 8000 chars to stay within AI token limits. |

---

## Tech stack

| Technology | Version |
|---|---|
| Java | 17 |
| Spring Boot | 3.2.3 |
| Apache Kafka | 7.6.0 |
| RabbitMQ | 3.13 |
| Redis | 7 |
| PostgreSQL | 16 |
| Google Gemini | 1.5 Flash |
| Apache PDFBox | 3.0.1 |
| Apache HttpClient | 5 |
| Lombok | latest |
| Docker Compose | - |

---

## Project structure

```
src/main/java/com/resumescreening/
│
├── ResumeScreeningApplication.java
│
├── config/
│   ├── KafkaConfig.java            # topic (6 partitions), producer, consumer factory
│   ├── RabbitMQConfig.java         # exchange, queue, binding, JSON converter
│   ├── RedisConfig.java            # RedisTemplate with JSON serializer
│   └── RestTemplateConfig.java     # connection pool + 30s timeout for Gemini calls
│
├── controller/
│   ├── ApplicationController.java  # POST /api/applications (multipart)
│   ├── DashboardController.java    # GET  /api/dashboard/applications
│   └── JobPostingController.java   # POST/GET /api/jobs
│
├── dto/
│   ├── ApplicationSubmissionRequest.java   # form input from candidate
│   ├── ResumeSubmittedEvent.java           # Kafka message payload
│   ├── HrNotificationEvent.java            # RabbitMQ message payload
│   ├── ScoringResult.java                  # AI scoring output
│   └── ApplicationDashboardDto.java        # HR dashboard response
│
├── entity/
│   ├── Candidate.java      # candidate profile + parsed resume text
│   ├── JobPosting.java     # title, description, requiredSkills (text[])
│   └── Application.java    # links candidate to job, holds score and status
│
├── repository/
│   ├── CandidateRepository.java
│   ├── JobPostingRepository.java
│   └── ApplicationRepository.java  # filtered/paginated dashboard queries
│
├── service/
│   ├── ResumeProcessService.java   # @Transactional - 6-step ingestion
│   ├── PdfParsingService.java      # PDFBox, 5MB limit, 8000 char trim
│   ├── NotificationService.java    # publishes to RabbitMQ if score > 7.0
│   ├── CacheService.java           # Redis read/write with TTL
│   └── DashboardService.java       # Redis-first, PostgreSQL fallback
│
├── kafka/
│   └── ScoringConsumerService.java # @KafkaListener, 3 threads, idempotent
│
├── rabbitmq/
│   └── EmailConsumerService.java   # @RabbitListener, sends HTML email to HR
│
└── scoring/
    └── AiScoringEngine.java        # RestTemplate to Gemini API, parse JSON score
```

---

## Prerequisites

- Java 17+
- Maven 3.8+
- Docker + Docker Compose
- Google Gemini API key — **free, no credit card**
- Gmail app password — for HR email notifications

---

## Getting a free Gemini API key

1. Go to [aistudio.google.com](https://aistudio.google.com)
2. Sign in with your Google account
3. Click **Get API key** → **Create API key**
4. Copy the key

Free tier: **15 requests/minute · 1 million tokens/day**

---

## Setup and running

### 1. Clone

```bash
git clone https://github.com/yourname/resume-screening.git
cd resume-screening
```

### 2. Start infrastructure

```bash
docker-compose up -d
```

Starts PostgreSQL, Redis, Kafka + Kafka UI, RabbitMQ + management UI. Wait ~20s then verify:

```bash
docker-compose ps   # all services should show healthy or running
```

### 3. Set environment variables

```bash
export GEMINI_API_KEY=AIzaSy_your_key_here
export MAIL_USERNAME=your@gmail.com
export MAIL_PASSWORD=your_gmail_app_password
```

> **Gmail app password** — not your regular password. Go to: Google Account → Security → 2-Step Verification → App passwords → generate one for Mail.

### 4. Run

```bash
./mvnw spring-boot:run
# App starts on http://localhost:8080
```

---

## API reference

### 1 — Create a job posting

```bash
curl -X POST "http://localhost:8080/api/jobs" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Senior Backend Engineer",
    "description": "Looking for a Senior Backend Engineer with 4+ years in Java, Spring Boot, and distributed systems.",
    "requiredSkills": ["Java", "Spring Boot", "Kafka", "Redis", "PostgreSQL", "Microservices", "Docker"],
    "requiredExperience": "4+ years",
    "location": "Bangalore, India"
  }'
```

`201 Created` — copy the `id` as `JOB_ID`.

---

### 2 — Submit an application

```bash
export JOB_ID="paste-job-id-here"

curl -X POST "http://localhost:8080/api/applications" \
  -F 'application={
    "jobPostingId": "'"$JOB_ID"'",
    "fullName": "Arjun Mehta",
    "email": "arjun.mehta@gmail.com",
    "phone": "+91 98765 43210",
    "location": "Bangalore, India",
    "totalExperience": "6 years",
    "currentRole": "Backend Engineer at Google",
    "education": "B.Tech CSE, IIT Bombay 2018",
    "noticePeriod": "30 days",
    "expectedCtc": "40 LPA"
  };type=application/json' \
  -F 'resume=@/path/to/resume.pdf;type=application/pdf'
```

`202 Accepted` — scoring is async via Kafka. Copy `applicationId` as `APP_ID` and wait 10-30s.

> The `;type=application/json` and `;type=application/pdf` suffixes on `-F` are required — Spring Boot needs explicit MIME types to deserialize multipart parts correctly.

---

### 3 — Check score result

```bash
export APP_ID="paste-application-id-here"
curl -X GET "http://localhost:8080/api/dashboard/applications/$APP_ID"
```

```json
{
  "overallScore": 9.2,
  "jdAlignmentPercent": 95,
  "experiencePercent": 88,
  "technicalDepthPercent": 91,
  "matchedSkills": ["Java", "Spring Boot", "Kafka", "Redis", "PostgreSQL"],
  "missingSkills": ["Docker"],
  "scoringReason": "Strong match. 6 years Java/Spring Boot aligns well. Kafka and Redis confirmed. Docker not mentioned.",
  "status": "SHORTLISTED",
  "hrNotified": true
}
```

---

### 4 — HR dashboard

```bash
# All applications for a job, sorted by score DESC
curl "http://localhost:8080/api/dashboard/applications?jobPostingId=$JOB_ID&page=0&size=20"

# Shortlisted only (score > 7)
curl "http://localhost:8080/api/dashboard/applications?jobPostingId=$JOB_ID&minScore=7&status=SHORTLISTED"

# Review candidates (score 5-7)
curl "http://localhost:8080/api/dashboard/applications?jobPostingId=$JOB_ID&status=REVIEW"

# Job stats summary
curl "http://localhost:8080/api/dashboard/jobs/$JOB_ID/stats"
# returns: { "total": 42, "shortlisted": 8, "review": 14, "lowMatch": 20 }

# Deactivate a job posting
curl -X PATCH "http://localhost:8080/api/jobs/$JOB_ID/deactivate"
```

---

## Scoring logic

Gemini receives the job description, required skills, and resume text and returns structured JSON scored across three dimensions.

| Dimension | Weight | What it measures |
|---|---|---|
| JD Alignment | 40% | Overall experience vs role requirements |
| Experience Relevance | 35% | Years, seniority, domain |
| Technical Depth | 25% | Required skills matched in resume |

**Score routing:**

| Score | Status | Action |
|---|---|---|
| > 7.0 | `SHORTLISTED` | HR email sent via RabbitMQ |
| 5.0 – 7.0 | `REVIEW` | Visible on dashboard, no email |
| < 5.0 | `LOW_MATCH` | Visible on dashboard, no email |

Threshold is configurable:

```yaml
app:
  scoring:
    threshold: 7.0
```

---

## Database schema

```
candidates              applications                  job_postings
──────────              ────────────                  ────────────
id UUID PK              id UUID PK                    id UUID PK
full_name               candidate_id     FK           title
email (unique)          job_posting_id   FK           description TEXT
phone                   overall_score                 required_skills TEXT[]
location                jd_alignment_percent          required_experience
total_experience        experience_percent            location
current_role            technical_depth_percent       active BOOLEAN
education               scoring_reason TEXT           created_at
notice_period           status ENUM
expected_ctc            scoring_status ENUM
resume_text TEXT        hr_notified BOOLEAN
resume_file_name        applied_at
created_at              scored_at
```

---

## Infrastructure UIs

| Service | URL | Credentials |
|---|---|---|
| Kafka UI | http://localhost:8090 | none |
| RabbitMQ Management | http://localhost:15672 | guest / guest |

---

## Environment variables

| Variable | Required | Description |
|---|---|---|
| `GEMINI_API_KEY` | Yes | From aistudio.google.com — free |
| `MAIL_USERNAME` | Yes | Gmail address to send from |
| `MAIL_PASSWORD` | Yes | Gmail app password |

---

## Common errors

| Error | Cause | Fix |
|---|---|---|
| `400 Only PDF files accepted` | Wrong file type | Add `;type=application/pdf` to the `-F` flag |
| `409 Candidate already applied` | Duplicate submission | Same email can only apply once per job |
| `scoringStatus: FAILED` | Gemini API error | Check `GEMINI_API_KEY` and rate limit (15 req/min) |
| `scoringStatus: PENDING` after 60s | Kafka consumer lagging | Check app logs and topic offset in Kafka UI |
| HR email not received | Wrong SMTP credentials | Use Gmail app password, not account password |

---

## Configuration reference

```yaml
app:
  scoring:
    threshold: 7.0                    # score cutoff to notify HR
    gemini-api-key: ${GEMINI_API_KEY}
    gemini-model: gemini-1.5-flash
  hr:
    email: hr@yourcompany.com         # HR inbox for shortlist emails
  redis:
    score-ttl-hours: 24
    candidate-ttl-hours: 48
  kafka:
    topic:
      resume-submitted: resume-submissions
  rabbitmq:
    exchange: resume-screening-exchange
    queue:
      hr-notification: hr-notification-queue
```
