package kr.securityadvisor.document.service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import kr.securityadvisor.common.schema.ExportFormat;
import org.springframework.stereotype.Component;

/** 형식별 변환기 목록. 같은 형식의 구현이 둘이면 기동 시 실패시켜 모호함을 막는다. */
@Component
public class DocumentExporters {

    private final Map<ExportFormat, DocumentExporter> byFormat = new EnumMap<>(ExportFormat.class);

    public DocumentExporters(List<DocumentExporter> exporters) {
        for (DocumentExporter exporter : exporters) {
            DocumentExporter previous = byFormat.putIfAbsent(exporter.format(), exporter);
            if (previous != null) {
                throw new IllegalStateException("출력 형식 " + exporter.format() + " 변환기가 둘 이상입니다: "
                        + previous.getClass().getSimpleName() + ", "
                        + exporter.getClass().getSimpleName());
            }
        }
    }

    public Optional<DocumentExporter> find(ExportFormat format) {
        return Optional.ofNullable(byFormat.get(format));
    }

    public boolean supports(ExportFormat format) {
        return byFormat.containsKey(format);
    }
}
