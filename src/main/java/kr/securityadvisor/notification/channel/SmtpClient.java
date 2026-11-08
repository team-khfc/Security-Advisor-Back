package kr.securityadvisor.notification.channel;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * 외부 라이브러리 없는 최소 SMTP 클라이언트(RFC 5321). EHLO → (STARTTLS) → (AUTH LOGIN) → MAIL FROM → RCPT TO → DATA → QUIT.
 * 기관 내부 메일 릴레이(인증 없는 25번 포트)를 기본 전제로 하고, 인증·STARTTLS 는 설정이 있을 때만 쓴다.
 * 제목·본문은 UTF-8 base64 로 인코딩한다. 응답 코드가 기대와 다르면 예외.
 */
public final class SmtpClient {

    private final NotificationChannelProperties.Smtp settings;

    public SmtpClient(NotificationChannelProperties.Smtp settings) {
        this.settings = settings;
    }

    public void send(String to, String subject, String body) {
        try (Socket plain = new Socket()) {
            plain.connect(new InetSocketAddress(settings.host(), settings.port()), settings.timeoutSeconds() * 1000);
            plain.setSoTimeout(settings.timeoutSeconds() * 1000);
            Session session = new Session(plain);
            session.expect("220");
            session.command("EHLO securityadvisor", "250");
            if (settings.starttls()) {
                session.command("STARTTLS", "220");
                SSLSocket tls = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                        .createSocket(plain, settings.host(), settings.port(), true);
                tls.startHandshake();
                session = new Session(tls);
                session.command("EHLO securityadvisor", "250");
            }
            if (settings.username() != null && !settings.username().isBlank()) {
                session.command("AUTH LOGIN", "334");
                session.command(base64(settings.username()), "334");
                session.command(base64(settings.password() == null ? "" : settings.password()), "235");
            }
            session.command("MAIL FROM:<" + settings.from() + ">", "250");
            session.command("RCPT TO:<" + to + ">", "250");
            session.command("DATA", "354");
            session.raw(message(to, subject, body));
            session.command(".", "250");
            session.command("QUIT", "221");
        } catch (IOException e) {
            throw new UncheckedIOException("SMTP 전송 실패", e);
        }
    }

    private String message(String to, String subject, String body) {
        String encodedSubject = "=?UTF-8?B?" + base64(subject) + "?=";
        String encodedBody = Base64.getMimeEncoder(76, new byte[] {'\r', '\n'})
                .encodeToString(body.getBytes(StandardCharsets.UTF_8));
        return "From: " + settings.from() + "\r\n"
                + "To: " + to + "\r\n"
                + "Subject: " + encodedSubject + "\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Transfer-Encoding: base64\r\n"
                + "\r\n"
                + encodedBody;
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** 한 소켓 위의 명령·응답. 여러 줄 응답(250-…)은 마지막 줄(250 )까지 읽는다. */
    private static final class Session {
        private final BufferedReader in;
        private final PrintWriter out;

        Session(Socket socket) throws IOException {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            this.out =
                    new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII), false);
        }

        void command(String line, String expectedCode) throws IOException {
            out.print(line + "\r\n");
            out.flush();
            expect(expectedCode);
        }

        void raw(String data) {
            // DATA 본문 안의 선행 마침표는 점 하나를 더 붙인다(투명성 규칙)
            out.print(data.replace("\r\n.", "\r\n..") + "\r\n");
            out.flush();
        }

        void expect(String code) throws IOException {
            String line;
            do {
                line = in.readLine();
                if (line == null) {
                    throw new IOException("SMTP 연결이 끊겼습니다(기대 응답 " + code + ")");
                }
            } while (line.length() >= 4 && line.charAt(3) == '-');
            if (!line.startsWith(code)) {
                throw new IOException("SMTP 응답 오류(기대 " + code + "): " + line.substring(0, Math.min(line.length(), 60)));
            }
        }
    }
}
