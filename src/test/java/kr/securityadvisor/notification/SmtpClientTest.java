package kr.securityadvisor.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import kr.securityadvisor.notification.channel.NotificationChannelProperties;
import kr.securityadvisor.notification.channel.SmtpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 최소 SMTP 클라이언트가 가짜 서버와 RFC 5321 순서대로 대화하고, 한글 제목·본문을 UTF-8 base64 로 보낸다. */
class SmtpClientTest {

    @Test
    @DisplayName("STARTTLS 미지원 서버에는 인증 정보나 메일을 보내지 않는다")
    void rejectsPlaintext() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<List<String>> received = CompletableFuture.supplyAsync(() -> fakeSmtp(server, false));
            NotificationChannelProperties.Smtp settings = new NotificationChannelProperties.Smtp(
                    "127.0.0.1",
                    server.getLocalPort(),
                    true,
                    "synthetic-user",
                    "synthetic-password",
                    "noreply@demo.invalid",
                    5);
            assertThatThrownBy(() -> new SmtpClient(settings).send("applicant@demo.invalid", "합성 제목", "합성 본문"))
                    .hasMessageContaining("SMTP");
            assertThat(received.get(10, java.util.concurrent.TimeUnit.SECONDS))
                    .noneMatch(line -> line.startsWith("AUTH")
                            || line.startsWith("MAIL")
                            || line.startsWith("RCPT")
                            || line.equals("DATA"));
        }
    }

    /** 명령마다 정해진 코드로 답하는 가짜 서버. rejectRcpt 면 RCPT TO 에 550. */
    private static List<String> fakeSmtp(ServerSocket server, boolean rejectRcpt) {
        List<String> lines = new ArrayList<>();
        try (Socket socket = server.accept();
                BufferedReader in =
                        new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
            out.print("220 fake ESMTP\r\n");
            out.flush();
            String line;
            boolean data = false;
            while ((line = in.readLine()) != null) {
                lines.add(line);
                if (data) {
                    if (line.equals(".")) {
                        data = false;
                        reply(out, "250 queued");
                    }
                    continue;
                }
                if (line.startsWith("EHLO")) {
                    reply(out, "250-fake\r\n250 OK");
                } else if (line.startsWith("MAIL FROM")) {
                    reply(out, "250 OK");
                } else if (line.startsWith("RCPT TO")) {
                    reply(out, rejectRcpt ? "550 no such user" : "250 OK");
                } else if (line.equals("DATA")) {
                    data = true;
                    reply(out, "354 go");
                } else if (line.equals("QUIT")) {
                    reply(out, "221 bye");
                    break;
                } else {
                    reply(out, "250 OK");
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return lines;
    }

    private static void reply(PrintWriter out, String text) {
        out.print(text + "\r\n");
        out.flush();
    }
}
