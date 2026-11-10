package kr.securityadvisor.request.service;

import java.time.Clock;
import java.util.*;
import kr.securityadvisor.common.security.MemberPrincipal;
import kr.securityadvisor.request.domain.ConsultationRecord;
import kr.securityadvisor.request.dto.ConsultationOverview.Field;
import kr.securityadvisor.request.repository.ConsultationRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional
@RequiredArgsConstructor
public class ConsultationRecorder {
    private final ConsultationRecordRepository repository;
    private final ObjectMapper json;
    private final Clock clock;

    public record Payload(Map<String, Field> before, Map<String, Field> after) {}

    public void baseline(MemberPrincipal actor, Long id, Map<String, Field> fields) {
        append(actor, id, "BASELINE", new Payload(Map.of(), fields));
    }

    public void change(
            MemberPrincipal actor, List<Long> ids, String kind, Map<String, Field> before, Map<String, Field> after) {
        Set<String> keys = new TreeSet<>(before.keySet());
        keys.addAll(after.keySet());
        Map<String, Field> old = new LinkedHashMap<>(), next = new LinkedHashMap<>();
        for (String key : keys)
            if (!Objects.equals(before.get(key), after.get(key))) {
                if (before.containsKey(key)) old.put(key, before.get(key));
                if (after.containsKey(key)) next.put(key, after.get(key));
            }
        if (old.isEmpty() && next.isEmpty() && !kind.equals("CONFIRM")) return;
        // 확인만 한 행위도 원래 값과 확인 이후 상태를 보존한다.
        if (kind.equals("CONFIRM") && old.isEmpty() && next.isEmpty()) {
            old.putAll(before);
            next.putAll(after);
        }
        for (Long id : ids) append(actor, id, kind, new Payload(old, next));
    }

    public void append(MemberPrincipal actor, Long id, String kind, Object payload) {
        repository.save(new ConsultationRecord(
                id, kind, actor.displayName(), actor.role().name(), clock.instant(), json.writeValueAsString(payload)));
    }

    public Payload read(ConsultationRecord record) {
        return json.readValue(record.getPayload(), Payload.class);
    }
}
