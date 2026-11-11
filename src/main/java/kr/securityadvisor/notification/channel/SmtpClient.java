package kr.securityadvisor.notification.channel;

import jakarta.mail.MessagingException;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

/** 표준 메일 구현으로 TLS와 서버 인증서 검증을 강제한다. 평문 전송으로 폴백하지 않는다. */
public final class SmtpClient {
    private final NotificationChannelProperties.Smtp settings;
    private final JavaMailSenderImpl sender;

    public SmtpClient(NotificationChannelProperties.Smtp settings) {
        this.settings = settings;
        this.sender = configuredSender(settings);
    }

    static JavaMailSenderImpl configuredSender(NotificationChannelProperties.Smtp settings) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.host());
        sender.setPort(settings.port());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        sender.setUsername(settings.username());
        sender.setPassword(settings.password());
        var properties = sender.getJavaMailProperties();
        properties.setProperty(
                "mail.smtp.auth",
                Boolean.toString(
                        settings.username() != null && !settings.username().isBlank()));
        properties.setProperty("mail.smtp.starttls.enable", Boolean.toString(settings.starttls()));
        properties.setProperty("mail.smtp.starttls.required", Boolean.toString(settings.starttls()));
        properties.setProperty("mail.smtp.ssl.enable", Boolean.toString(!settings.starttls()));
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.ssl.protocols", "TLSv1.3 TLSv1.2");
        String timeout = Integer.toString(Math.multiplyExact(settings.timeoutSeconds(), 1000));
        properties.setProperty("mail.smtp.connectiontimeout", timeout);
        properties.setProperty("mail.smtp.timeout", timeout);
        properties.setProperty("mail.smtp.writetimeout", timeout);
        return sender;
    }

    public void send(String to, String subject, String body) {
        try {
            var message = sender.createMimeMessage();
            var helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setValidateAddresses(true);
            helper.setFrom(settings.from());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            sender.send(message);
        } catch (MessagingException | MailException e) {
            throw new IllegalStateException("SMTP 전송 실패", e);
        }
    }
}
