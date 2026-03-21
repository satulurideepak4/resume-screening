package com.resume.screening.scoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume.screening.dto.ScoringResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AiScoringEngine {

    private static final String ANTHROPIC_API_URL = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION  = "2023-06-01";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.scoring.anthropic-api-key}")
    private String apiKey;

    @Value("${app.scoring.anthropic-model}")
    private String model;

    public AiScoringEngine(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Calls the Anthropic API with the JD + resume text.
     * Returns a structured ScoringResult parsed from the AI JSON response.
     */
    public ScoringResult score(String jobDescription, String resumeText, String jobTitle) {
        String prompt = buildPrompt(jobDescription, resumeText, jobTitle);
        try {
            String aiText = callAnthropicApi(prompt);
            return parseResponse(aiText);
        } catch (Exception e) {
            log.error("AI scoring failed", e);
            return ScoringResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .overallScore(0)
                    .build();
        }
    }

    private String buildPrompt(String jobDescription, String resumeText, String jobTitle) {
        return """
            You are an expert technical recruiter scoring a resume against a job description.

            ## Job Title
            %s

            ## Job Description
            %s

            ## Candidate Resume
            %s

            ## Instructions
            Evaluate the resume against the job description across three dimensions:
            1. JD Alignment (0-100): How well the candidate's experience matches the specific requirements
            2. Experience Relevance (0-100): Years, seniority level, and domain relevance
            3. Technical Depth (0-100): Depth of technical skills required for this role

            Compute an overall score (0.0 to 10.0) as a weighted average:
            - JD Alignment: 40%%
            - Experience Relevance: 35%%
            - Technical Depth: 25%%

            Respond ONLY with a valid JSON object. No markdown, no explanation outside JSON:
            {
              "overallScore": <number 0.0-10.0, one decimal place>,
              "jdAlignmentPercent": <integer 0-100>,
              "experiencePercent": <integer 0-100>,
              "technicalDepthPercent": <integer 0-100>,
              "reason": "<2-3 sentences explaining the score, key matches and gaps>"
            }
            """.formatted(jobTitle, jobDescription, resumeText);
    }

    private String callAnthropicApi(String prompt) {
        // Build headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", ANTHROPIC_VERSION);

        // Build request body
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 512,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    ANTHROPIC_API_URL,
                    HttpMethod.POST,
                    request,
                    String.class
            );
            return extractTextFromAnthropicResponse(response.getBody());

        } catch (HttpClientErrorException e) {
            // 4xx — bad request, auth failure, etc.
            throw new RuntimeException(
                    "Anthropic API client error " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        } catch (HttpServerErrorException e) {
            // 5xx — Anthropic side issue
            throw new RuntimeException(
                    "Anthropic API server error " + e.getStatusCode() + ": " + e.getResponseBodyAsString(), e);
        }
    }

    private String extractTextFromAnthropicResponse(String rawApiResponse) {
        try {
            JsonNode root = objectMapper.readTree(rawApiResponse);
            // Anthropic response shape: { "content": [ { "type": "text", "text": "..." } ] }
            return root.path("content").get(0).path("text").asText();
        } catch (Exception e) {
            throw new RuntimeException("Could not parse Anthropic API response: " + rawApiResponse, e);
        }
    }

    private ScoringResult parseResponse(String aiText) {
        try {
            // Strip any accidental markdown fences the model may add
            String json = aiText
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();

            JsonNode node = objectMapper.readTree(json);

            double overall = node.path("overallScore").asDouble();
            int jdAlign    = node.path("jdAlignmentPercent").asInt();
            int expPct     = node.path("experiencePercent").asInt();
            int techPct    = node.path("technicalDepthPercent").asInt();
            String reason  = node.path("reason").asText();

            // Sanity clamp — never trust raw AI numbers blindly
            overall = Math.min(10.0, Math.max(0.0, overall));
            jdAlign = clamp(jdAlign);
            expPct  = clamp(expPct);
            techPct = clamp(techPct);

            log.info("Scored resume: overall={} jd={}% exp={}% tech={}%",
                    overall, jdAlign, expPct, techPct);

            return ScoringResult.builder()
                    .overallScore(overall)
                    .jdAlignmentPercent(jdAlign)
                    .experiencePercent(expPct)
                    .technicalDepthPercent(techPct)
                    .reason(reason)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse AI scoring response: {}", aiText, e);
            throw new RuntimeException("Could not parse AI scoring JSON: " + e.getMessage(), e);
        }
    }

    private int clamp(int value) {
        return Math.min(100, Math.max(0, value));
    }
}