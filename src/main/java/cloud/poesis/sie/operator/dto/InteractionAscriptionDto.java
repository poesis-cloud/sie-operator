package cloud.poesis.sie.operator.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record InteractionAscriptionDto(
    UUID id, String status, int version, UUID effector, UUID receptor) {

  @JsonCreator
  public static InteractionAscriptionDto fromJson(
      @JsonProperty("id") UUID id,
      @JsonProperty("status") String status,
      @JsonProperty("version") int version,
      @JsonProperty("statement") JsonNode statement) {
    return new InteractionAscriptionDto(
        id,
        status,
        version,
        UUID.fromString(statement.path("effector").asText()),
        UUID.fromString(statement.path("receptor").asText()));
  }
}
