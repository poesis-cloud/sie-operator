package cloud.poesis.sie.operator.dto;

import cloud.poesis.sie.operator.starlark.EffectRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Collections;
import java.util.List;

@Schema(name = "OperationResponse", description = "Result of a mechanism operation execution")
public record OperationResponseDto(
    @Schema(description = "Whether the operation completed successfully") boolean success,
    @Schema(description = "Effects produced by the mechanism rule execution")
        List<EffectRecord> effects,
    @Schema(description = "Error message when the operation failed") String error) {

  public OperationResponseDto {
    effects = effects != null ? List.copyOf(effects) : List.of();
  }

  public static OperationResponseDto success(List<EffectRecord> effects) {
    return new OperationResponseDto(true, effects, null);
  }

  public static OperationResponseDto failure(String error) {
    return new OperationResponseDto(false, Collections.emptyList(), error);
  }
}
