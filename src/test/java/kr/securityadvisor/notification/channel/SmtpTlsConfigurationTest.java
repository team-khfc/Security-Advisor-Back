package kr.securityadvisor.notification.channel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SmtpTlsConfigurationTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void requiresEncryptionAndServerIdentity(boolean starttls) {
        var settings = new NotificationChannelProperties.Smtp(
                "smtp.example.invalid", starttls ? 587 : 465, starttls, null, null, "sender@example.invalid", 5);
        var properties = SmtpClient.configuredSender(settings).getJavaMailProperties();
        assertThat(properties.getProperty("mail.smtp.starttls.required")).isEqualTo(Boolean.toString(starttls));
        assertThat(properties.getProperty("mail.smtp.ssl.enable")).isEqualTo(Boolean.toString(!starttls));
        assertThat(properties.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");
        assertThat(properties.getProperty("mail.smtp.timeout")).isEqualTo("5000");
    }
}
