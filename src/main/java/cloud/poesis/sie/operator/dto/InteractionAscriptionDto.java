package cloud.poesis.sie.operator.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.UUID;

public record InteractionAscriptionDto(
    UUID id, String status, int version, UUID effector, UUID receptor) {

  public InteractionAscriptionDto {
    Objects.requireNonNull(id, "InteractionAscriptionDto: id is required");
    Objects.requireNonNull(status, "InteractionAscriptionDto: status is required");
    Objects.requireNonNull(
        effector, "InteractionAscriptionDto: statement field 'effector' is required");
    Objects.requireNonNull(
        receptor, "InteractionAscriptionDto: statement field 'receptor' is required");
  }

  @JsonCreator
  public static InteractionAscriptionDto fromJson(
      @JsonProperty("id") UUID id,
      @JsonProperty("status") String status,
      @JsonProperty("version") int version,
      @JsonProperty("statement") JsonNode statement) {
    Objects.requireNonNull(statement, "InteractionAscriptionDto: statement is required");
    String effectorText = statement.path("effector").asText();
    if (effectorText.isBlank()) {
      throw new IllegalArgumentException(
          "InteractionAscriptionDto: statement field 'effector' is required");
    }
    String receptorText = statement.path("receptor").asText();
    if (receptorText.isBlank()) {
      throw new IllegalArgumentException(
          "InteractionAscriptionDto: statement field 'receptor' is required");
    }
    return new InteractionAscriptionDto(
        id, status, version, UUID.fromString(effectorText), UUID.fromString(receptorText));
  }
}
