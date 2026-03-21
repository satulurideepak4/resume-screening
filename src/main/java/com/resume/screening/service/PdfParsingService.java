package com.resume.screening.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Slf4j
@Service
public class PdfParsingService {

    private static final int MAX_RESUME_CHARS = 8000; // trim to avoid huge AI prompts

    /**
     * Extracts plain text from an uploaded PDF resume.
     * Trims to MAX_RESUME_CHARS to keep AI prompts within token limits.
     */
    public String extractText(MultipartFile file) {
        validateFile(file);
        try (PDDocument document = Loader.loadPDF(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);   // maintains reading order
            String rawText = stripper.getText(document);
            String cleaned = cleanText(rawText);
            log.debug("Extracted {} characters from resume: {}", cleaned.length(), file.getOriginalFilename());
            return trimToLimit(cleaned);
        } catch (IOException e) {
            log.error("Failed to parse PDF: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("Could not parse resume PDF: " + e.getMessage(), e);
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Resume file is required");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equals("application/pdf")) {
            throw new IllegalArgumentException("Only PDF files are accepted. Received: " + contentType);
        }
        // 5 MB limit
        if (file.getSize() > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Resume file must be under 5 MB");
        }
    }

    private String cleanText(String raw) {
        return raw
                .replaceAll("\\r\\n|\\r", "\n")          // normalise line endings
                .replaceAll("[ \\t]{2,}", " ")            // collapse multiple spaces
                .replaceAll("\\n{3,}", "\n\n")            // max 2 consecutive blank lines
                .trim();
    }

    private String trimToLimit(String text) {
        if (text.length() <= MAX_RESUME_CHARS) return text;
        log.warn("Resume text trimmed from {} to {} chars", text.length(), MAX_RESUME_CHARS);
        return text.substring(0, MAX_RESUME_CHARS) + "\n[... resume trimmed for scoring ...]";
    }
}