package com.velocura.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ConsoleNotificationServiceImpl implements NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(ConsoleNotificationServiceImpl.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    private void sendEmail(String toEmail, String subject, String body) {
        // Fallback check: if real SMTP parameters are configured, send real email asynchronously
        if (mailSender != null && mailUsername != null && !mailUsername.trim().isEmpty()) {
            executorService.submit(() -> {
                try {
                    jakarta.mail.internet.MimeMessage message = mailSender.createMimeMessage();
                    org.springframework.mail.javamail.MimeMessageHelper helper = new org.springframework.mail.javamail.MimeMessageHelper(message, "UTF-8");
                    helper.setFrom(new jakarta.mail.internet.InternetAddress(mailUsername, "VeloCura Healthcare"));
                    helper.setTo(toEmail);
                    helper.setSubject(subject);
                    helper.setText(body);
                    mailSender.send(message);
                    logger.info("📬 REAL SMTP EMAIL SENT SUCCESSFULLY TO: {}", toEmail);
                } catch (Exception e) {
                    logger.error("❌ FAILED TO DISPATCH REAL SMTP EMAIL to: {}. Error: {}", toEmail, e.getMessage());
                }
            });
        }
    }

    @Override
    public void sendWelcomeEmail(String toEmail, String name) {
        String subject = "Welcome to VeloCura!";
        String body = "Hello " + name + ",\n\nThank you for registering with VeloCura Healthcare Platform.\nYour account is now ready for use.\n\nBest regards,\nThe VeloCura Team";

        logger.info("------------------------------------------------------------");
        logger.info("SMTP EMAIL OUTBOX [Welcome]");
        logger.info("TO: {} ({})", toEmail, name);
        logger.info("SUBJECT: {}", subject);
        logger.info("BODY: {}", body.replace("\n", " "));
        logger.info("------------------------------------------------------------");

        sendEmail(toEmail, subject, body);
    }

    @Override
    public void sendAppointmentBookingEmail(String toEmail, String name, String doctorName, LocalDateTime time) {
        String subject = "Appointment Confirmed - VeloCura";
        String body = "Hello " + name + ",\n\nYour telehealth consultation with " + doctorName + " has been scheduled and confirmed for " + time.format(formatter) + ".\n\nBest regards,\nThe VeloCura Team";

        logger.info("------------------------------------------------------------");
        logger.info("SMTP EMAIL OUTBOX [Booking Alert]");
        logger.info("TO: {} ({})", toEmail, name);
        logger.info("SUBJECT: {}", subject);
        logger.info("BODY: {}", body.replace("\n", " "));
        logger.info("------------------------------------------------------------");

        sendEmail(toEmail, subject, body);
    }

    @Override
    public void sendDoctorVerificationEmail(String toEmail, String name) {
        String subject = "Doctor Credentials Verified - VeloCura";
        String body = "Hello Dr. " + name + ",\n\nYour credentials have been successfully reviewed and verified by our administration. Your profile is now active on the provider network.\n\nBest regards,\nThe VeloCura Team";

        logger.info("------------------------------------------------------------");
        logger.info("SMTP EMAIL OUTBOX [Credential Verification]");
        logger.info("TO: {} ({})", toEmail, name);
        logger.info("SUBJECT: {}", subject);
        logger.info("BODY: {}", body.replace("\n", " "));
        logger.info("------------------------------------------------------------");

        sendEmail(toEmail, subject, body);
    }

    @Override
    public void sendOtpEmail(String toEmail, String code) {
        String subject = "Your VeloCura Verification Code";
        String body = "Use OTP code " + code + " to complete your sign-in / registration verification. Expiries in 5 minutes.";

        logger.info("------------------------------------------------------------");
        logger.info("SMTP EMAIL OUTBOX [Security OTP Verification]");
        logger.info("TO: {}", toEmail);
        logger.info("SUBJECT: {}", subject);
        logger.info("BODY: Use OTP code [REDACTED] to complete your sign-in / registration verification. Expires in 5 minutes.");
        logger.info("------------------------------------------------------------");

        sendEmail(toEmail, subject, body);
    }
}
