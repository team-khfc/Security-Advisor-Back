package kr.securityadvisor.request.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import kr.securityadvisor.request.dto.InfrastructurePlanCommand.Connection;
import kr.securityadvisor.request.dto.InfrastructurePlanCommand.Server;

public record InfrastructurePlanResponse(
        long revision,
        List<Connection> connections,
        String notes,
        String updatedBy,
        Instant updatedAt,
        int schemaVersion,
        boolean draft,
        boolean aiPending,
        List<Server> servers,
        Map<String, String> operations,
        Map<String, Boolean> checklist) {
    public InfrastructurePlanResponse {
        servers = servers == null ? List.of() : List.copyOf(servers);
        operations = operations == null ? Map.of() : Map.copyOf(operations);
        checklist = checklist == null ? Map.of() : Map.copyOf(checklist);
    }
}
