package cloud.poesis.sie.operator.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record ReceptorAscriptionDto(
    UUID id, String status, int version, UUID mechanism, UUID archetype) {

  @JsonCreator
  public static ReceptorAscriptionDto fromJson(
      @JsonProperty("id") UUID id,
      @JsonProperty("status") String status,
      @JsonProperty("version") int version,
      @JsonProperty("statement") JsonNode statement) {
    return new ReceptorAscriptionDto(
        id,
        status,
        version,
        UUID.fromString(statement.path("mechanism").asText()),
        UUID.fromString(statement.path("archetype").asText()));
  }
}
