package com.resume.screening.rabbitMq;

import com.resume.screening.dto.HrNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailConsumerService {

    private final JavaMailSender mailSender;

    @Value("${app.hr.email}")
    private String hrEmail;

    @Value("${spring.mail.username}")
    private String fromEmail;

    /**
     * Listens on the HR notification queue.
     * Builds and sends a formatted HTML email to the HR team.
     */
    @RabbitListener(queues = "${app.rabbitmq.queue.hr-notification}")
    public void handleHrNotification(HrNotificationEvent event) {
        log.info("Received HR notification event for candidate={} score={}",
                event.getCandidateFullName(), event.getOverallScore());
        try {
            sendHrEmail(event);
            log.info("HR email sent successfully for candidate={} application={}",
                    event.getCandidateFullName(), event.getApplicationId());
        } catch (Exception e) {
            log.error("Failed to send HR email for application={}", event.getApplicationId(), e);
            // RabbitMQ will redeliver if an exception propagates — rethrow to trigger requeue
            throw new RuntimeException("Email sending failed", e);
        }
    }

    private void sendHrEmail(HrNotificationEvent event) throws Exception {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setFrom(fromEmail);
        helper.setTo(hrEmail);
        helper.setSubject(String.format("[Shortlisted] %s — %.1f/10 for %s",
                event.getCandidateFullName(),
                event.getOverallScore(),
                event.getJobTitle()));

        helper.setText(buildEmailHtml(event), true);
        mailSender.send(message);
    }

    private String buildEmailHtml(HrNotificationEvent event) {
        String scoreColor = event.getOverallScore() >= 9.0 ? "#1D9E75"
                : event.getOverallScore() >= 8.0 ? "#1D9E75"
                : "#BA7517";

        return """
            <!DOCTYPE html>
            <html>
            <body style="font-family: Arial, sans-serif; background: #f5f5f5; padding: 24px;">
              <div style="max-width: 600px; margin: 0 auto; background: #ffffff;
                          border-radius: 8px; overflow: hidden; border: 1px solid #e0e0e0;">

                <!-- Header -->
                <div style="background: #1a1a2e; padding: 24px 32px;">
                  <h2 style="color: #ffffff; margin: 0; font-size: 18px;">
                    Shortlisted Candidate Alert
                  </h2>
                  <p style="color: #aaaacc; margin: 6px 0 0; font-size: 13px;">
                    Resume Screening Pipeline
                  </p>
                </div>

                <!-- Score Banner -->
                <div style="background: #f9f9f9; padding: 20px 32px;
                             border-bottom: 1px solid #eeeeee; display: flex; align-items: center;">
                  <div>
                    <p style="margin: 0; font-size: 13px; color: #666;">Overall score</p>
                    <p style="margin: 4px 0 0; font-size: 36px; font-weight: bold; color: %s;">
                      %.1f<span style="font-size: 18px; color: #999;">/10</span>
                    </p>
                  </div>
                  <div style="margin-left: 40px;">
                    <p style="margin: 0; font-size: 13px; color: #666;">Position</p>
                    <p style="margin: 4px 0 0; font-size: 16px; font-weight: 500; color: #222;">%s</p>
                  </div>
                </div>

                <!-- Candidate Details -->
                <div style="padding: 24px 32px;">
                  <h3 style="margin: 0 0 16px; font-size: 15px; color: #222;">Candidate Details</h3>
                  <table style="width: 100%%; border-collapse: collapse; font-size: 13px;">
                    %s
                    %s
                    %s
                    %s
                  </table>
                </div>

                <!-- Score Breakdown -->
                <div style="padding: 0 32px 24px;">
                  <h3 style="margin: 0 0 16px; font-size: 15px; color: #222;">Score Breakdown</h3>
                  %s
                  %s
                  %s
                </div>

                <!-- AI Reasoning -->
                <div style="padding: 0 32px 24px;">
                  <h3 style="margin: 0 0 10px; font-size: 15px; color: #222;">AI Assessment</h3>
                  <p style="margin: 0; font-size: 13px; color: #444; line-height: 1.7;
                             background: #f5f7ff; padding: 14px 16px; border-radius: 6px;
                             border-left: 3px solid #7F77DD;">
                    %s
                  </p>
                </div>

                <!-- Footer -->
                <div style="padding: 16px 32px; background: #f9f9f9;
                             border-top: 1px solid #eeeeee; text-align: center;">
                  <p style="margin: 0; font-size: 11px; color: #999;">
                    This email was generated automatically by the Resume Screening Pipeline.
                    Application ID: %s
                  </p>
                </div>
              </div>
            </body>
            </html>
            """.formatted(
                scoreColor,
                event.getOverallScore(),
                event.getJobTitle(),
                detailRow("Full name",     event.getCandidateFullName()),
                detailRow("Email",         event.getCandidateEmail()),
                detailRow("Phone",         event.getCandidatePhone()),
                detailRow("Current role",  event.getCurrentRole() + " · " + event.getTotalExperience()),
                scoreBar("JD alignment",    event.getJdAlignmentPercent()),
                scoreBar("Experience",      event.getExperiencePercent()),
                scoreBar("Technical depth", event.getTechnicalDepthPercent()),
                event.getScoringReason(),
                event.getApplicationId()
        );
    }

    private String detailRow(String label, String value) {
        return """
            <tr>
              <td style="padding: 6px 0; color: #888; width: 140px;">%s</td>
              <td style="padding: 6px 0; color: #222; font-weight: 500;">%s</td>
            </tr>
            """.formatted(label, value == null ? "—" : value);
    }

    private String scoreBar(String label, int percent) {
        String color = percent >= 80 ? "#1D9E75" : percent >= 60 ? "#BA7517" : "#E24B4A";
        return """
            <div style="margin-bottom: 10px;">
              <div style="display: flex; justify-content: space-between;
                           font-size: 12px; color: #666; margin-bottom: 4px;">
                <span>%s</span><span style="font-weight: 500; color: #222;">%d%%</span>
              </div>
              <div style="background: #eeeeee; border-radius: 4px; height: 6px;">
                <div style="width: %d%%; background: %s; height: 6px; border-radius: 4px;"></div>
              </div>
            </div>
            """.formatted(label, percent, percent, color);
    }
}