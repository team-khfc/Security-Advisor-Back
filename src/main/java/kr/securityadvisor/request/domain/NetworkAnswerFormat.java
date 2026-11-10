package kr.securityadvisor.request.domain;

import java.net.InetAddress;
import java.util.Set;
import kr.securityadvisor.common.error.DomainRuleViolationException;
import kr.securityadvisor.common.schema.FieldDefinition;

/** DNS 조회 없이 네트워크 리터럴만 검사한다. 목록은 쉼표 또는 줄바꿈으로 구분한다. */
final class NetworkAnswerFormat {
    private NetworkAnswerFormat() {}

    static void validate(FieldDefinition field, String value) {
        String[] items = value.split("[,\\n]", -1);
        if (items.length > 100) invalid(field);
        for (String item : items) {
            String token = item.strip();
            boolean valid = switch (field.type()) {
                case "IP_ADDRESS" -> ip(token);
                case "PORT" -> port(token);
                case "PROTOCOL" ->
                    Set.of("TCP", "UDP", "ICMP", "ICMPV6", "ESP", "AH", "GRE").contains(token);
                default -> false;
            };
            if (!valid) invalid(field);
        }
    }

    private static boolean ip(String token) {
        String[] pieces = token.split("/", -1);
        if (pieces.length > 2 || !pieces[0].matches("[0-9a-fA-F:.]+")) return false;
        try {
            String host = pieces[0];
            // IPv4 축약·8진수 형태 및 IPv6에 내장된 모호한 IPv4를 허용하지 않는다.
            if (host.contains(".")) {
                String ipv4 = host.substring(host.lastIndexOf(':') + 1);
                String[] octets = ipv4.split("\\.", -1);
                if (octets.length != 4) return false;
                for (String octet : octets) {
                    if (!octet.matches("0|[1-9][0-9]{0,2}") || Integer.parseInt(octet) > 255) return false;
                }
            } else if (!host.contains(":")) return false;
            InetAddress.ofLiteral(host);
            return pieces.length == 1
                    || (pieces[1].matches("0|[1-9][0-9]{0,2}")
                            && Integer.parseInt(pieces[1]) <= (host.contains(":") ? 128 : 32));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean port(String token) {
        if (!token.matches("[0-9]{1,5}(?:\\s*-\\s*[0-9]{1,5})?")) return false;
        String[] range = token.split("\\s*-\\s*");
        int start = Integer.parseInt(range[0]), end = Integer.parseInt(range[range.length - 1]);
        return start >= 1 && end <= 65535 && start <= end;
    }

    private static void invalid(FieldDefinition field) {
        String format = switch (field.type()) {
            case "IP_ADDRESS" -> "IPv4·IPv6 주소 또는 올바른 CIDR";
            case "PORT" -> "1~65535 포트 또는 시작-종료 범위";
            default -> "TCP, UDP, ICMP, ICMPV6, ESP, AH, GRE 중 프로토콜";
        };
        throw new DomainRuleViolationException(
                field.label() + ": " + format + "을 입력해 주세요. 목록은 쉼표나 줄바꿈으로 구분하며 최대 100개입니다.");
    }
}
