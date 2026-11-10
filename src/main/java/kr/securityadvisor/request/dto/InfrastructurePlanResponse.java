package kr.securityadvisor.request.dto;

import java.time.Instant;
import java.util.List;
import kr.securityadvisor.request.dto.InfrastructurePlanCommand.Connection;

public record InfrastructurePlanResponse(
        long revision, List<Connection> connections, String notes, String updatedBy, Instant updatedAt) {}
