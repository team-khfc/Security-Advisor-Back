package kr.securityadvisor.request.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

public record InfrastructurePlanCommand(
        @NotNull @PositiveOrZero Long expectedRevision,
        @NotNull @Size(max = 50) List<@Valid Connection> connections,
        @NotNull @Size(max = 4000) String notes,
        @NotNull Map<String, Long> answerRevisions,
        @Min(0) @Max(2) int schemaVersion,
        boolean draft,
        boolean aiPending,
        @Size(max = 50) List<@Valid Server> servers,
        @Size(max = 10) Map<@Size(max = 40) String, @Size(max = 4000) String> operations,
        @Size(max = 10) Map<@Size(max = 40) String, Boolean> checklist) {
    public InfrastructurePlanCommand {
        servers = servers == null ? List.of() : List.copyOf(servers);
        operations = operations == null ? Map.of() : Map.copyOf(operations);
        checklist = checklist == null ? Map.of() : Map.copyOf(checklist);
    }

    public record Server(
            @NotBlank @Size(max = 100) String id,
            @NotNull @Size(max = 200) String name,
            @NotNull @Size(max = 1000) String purpose,
            @NotNull @Size(max = 30) String environment,
            @NotNull @Size(max = 200) String cpu,
            @NotNull @Size(max = 200) String memory,
            @NotNull @Size(max = 200) String storage,
            @NotNull @Size(max = 200) String os,
            @NotNull @Size(max = 200) String database,
            @NotNull @Size(max = 4000) String address,
            @NotNull @Size(max = 30) String zone,
            boolean aiInferred,
            boolean aiConfirmed,
            @Size(max = 12) Map<@Size(max = 40) String, @Size(max = 2500) String> evidence) {
        public Server {
            evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
        }
    }

    public record Connection(
            @NotBlank @Size(max = 100) String id,
            @NotNull @Size(max = 200) String sourceSystem,
            @NotNull @Size(max = 4000) String sourceAddress,
            @NotNull @Size(max = 30) String sourceZone,
            @NotNull @Size(max = 200) String destinationSystem,
            @NotNull @Size(max = 4000) String destinationAddress,
            @NotNull @Size(max = 30) String destinationZone,
            @NotNull @Size(max = 20) String protocol,
            @NotNull @Size(max = 500) String port,
            @NotNull @Size(max = 1000) String purpose,
            @NotNull @Size(max = 20) String direction,
            @NotNull @Size(max = 10) String startDate,
            @NotNull @Size(max = 10) String endDate,
            @Size(max = 100) String sourceServerId,
            @Size(max = 100) String destinationServerId) {}
}
