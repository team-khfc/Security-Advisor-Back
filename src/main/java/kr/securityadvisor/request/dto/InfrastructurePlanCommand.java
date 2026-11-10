package kr.securityadvisor.request.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;

public record InfrastructurePlanCommand(
        @NotNull @PositiveOrZero Long expectedRevision,
        @NotNull @Size(min = 1, max = 50) List<@Valid Connection> connections,
        @NotNull @Size(max = 4000) String notes,
        @NotNull Map<String, Long> answerRevisions) {
    public record Connection(
            @NotBlank @Size(max = 100) String id,
            @NotBlank @Size(max = 200) String sourceSystem,
            @NotBlank @Size(max = 4000) String sourceAddress,
            @NotBlank @Size(max = 30) String sourceZone,
            @NotBlank @Size(max = 200) String destinationSystem,
            @NotBlank @Size(max = 4000) String destinationAddress,
            @NotBlank @Size(max = 30) String destinationZone,
            @NotBlank @Size(max = 20) String protocol,
            @NotBlank @Size(max = 500) String port,
            @NotBlank @Size(max = 1000) String purpose,
            @NotBlank @Size(max = 20) String direction,
            @NotBlank String startDate,
            @NotBlank String endDate) {}
}
