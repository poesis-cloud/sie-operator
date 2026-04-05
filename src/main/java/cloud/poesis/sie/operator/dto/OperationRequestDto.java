package cloud.poesis.sie.operator.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

@Schema(
    name = "OperationRequest",
    description = "Request to execute a mechanism operation against the SIE Operator")
public record OperationRequestDto(
    @NotNull
        @Schema(
            description = "The ascription ID of the mechanism to execute",
            example = "550e8400-e29b-41d4-a716-446655440000")
        UUID mechanismAscriptionId,
    @NotNull
        @Schema(
            description =
                "The operation input — validated against the mechanism's trigger receptor archetype schema")
        Map<String, Object> operationInput) {

  public OperationRequestDto {
    operationInput =
        operationInput != null ? Collections.unmodifiableMap(operationInput) : Map.of();
  }
}
