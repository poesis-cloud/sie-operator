package cloud.poesis.sie.operator.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record MechanismAscriptionDto(
    UUID id, String status, int version, UUID structure, String function, String rule) {

  @JsonCreator
  public static MechanismAscriptionDto fromJson(
      @JsonProperty("id") UUID id,
      @JsonProperty("status") String status,
      @JsonProperty("version") int version,
      @JsonProperty("statement") JsonNode statement) {
    return new MechanismAscriptionDto(
        id,
        status,
        version,
        UUID.fromString(statement.path("structure").asText()),
        statement.path("function").asText(),
        statement.path("rule").asText());
  }
}
