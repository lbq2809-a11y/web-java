package com.pharmacy.notification.service;

import com.pharmacy.notification.document.EmailLogDocument;
import com.pharmacy.notification.repository.EmailLogRepository;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class EmailService {

    @Inject
    Mailer mailer;

    @Inject
    EmailLogRepository emailLogRepository;

    /**
     * Gửi email HTML.
     * Giữ lại phương thức cũ để tương thích với những nơi đang sử dụng.
     */
    public EmailLogDocument sendHtmlEmail(
            String recipient,
            String subject,
            String templateKey,
            String htmlContent
    ) {
        return sendHtmlEmail(
                null,
                recipient,
                subject,
                templateKey,
                htmlContent
        );
    }

    /**
     * Gửi email HTML, lưu lịch sử vào MongoDB.
     *
     * Chống gửi email trùng theo eventId.
     *
     * Không tự động retry khi SMTP timeout vì không thể
     * xác định chắc chắn server đã nhận email hay chưa.
     */
    public EmailLogDocument sendHtmlEmail(
            String eventId,
            String recipient,
            String subject,
            String templateKey,
            String htmlContent
    ) {

        validate(recipient, "Email recipient");
        validate(htmlContent, "Email content");
        validate(templateKey, "Template");

        // Kiểm tra eventId đã tồn tại hay chưa.
        // Nếu đã có bản ghi thì không gửi email lần nữa.
        if (eventId != null && !eventId.isBlank()) {

            EmailLogDocument existing = emailLogRepository
                    .findByEventId(eventId)
                    .orElse(null);

            if (existing != null) {
                return existing;
            }
        }

        // Tạo lịch sử gửi email mới.
        EmailLogDocument emailLog = new EmailLogDocument();

        emailLog.eventId = eventId;
        emailLog.recipient = recipient;
        emailLog.template = templateKey;
        emailLog.status = "PENDING";
        emailLog.attempts = 1;
        emailLog.createdAt = Instant.now();

        emailLogRepository.persist(emailLog);

        try {

            // Tạo email HTML.
            Mail mail = Mail.withHtml(
                    recipient,
                    subject,
                    htmlContent
            );

            // Gửi email qua MailHog hoặc SMTP Server.
            mailer.send(mail);

            // Gửi thành công.
            emailLog.status = "SENT";
            emailLog.sentAt = Instant.now();
            emailLog.error = null;

        } catch (Exception exception) {

            /*
             * SMTP timeout có thể xảy ra sau khi server
             * đã nhận email.
             *
             * Không retry tự động để tránh gửi email trùng.
             */
            emailLog.status = "UNKNOWN";
            emailLog.error = truncateError(exception);
        }

        // Cập nhật trạng thái email vào MongoDB.
        emailLogRepository.update(emailLog);

        return emailLog;
    }

    /**
     * Kiểm tra dữ liệu bắt buộc.
     */
    private void validate(String value, String fieldName) {

        if (value == null || value.isBlank()) {

            throw new IllegalArgumentException(
                    fieldName + " không được để trống"
            );
        }
    }

    /**
     * Giới hạn độ dài thông báo lỗi lưu trong MongoDB.
     */
    private String truncateError(Exception exception) {

        String message = exception.getClass().getSimpleName()
                + ": "
                + exception.getMessage();

        if (message.length() > 1000) {
            return message.substring(0, 1000);
        }

        return message;
    }
}