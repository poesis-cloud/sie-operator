package cloud.poesis.sie.operator.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record StructureAscriptionDto(UUID id, String status, int version, String purpose) {

  @JsonCreator
  public static StructureAscriptionDto fromJson(
      @JsonProperty("id") UUID id,
      @JsonProperty("status") String status,
      @JsonProperty("version") int version,
      @JsonProperty("statement") JsonNode statement) {
    return new StructureAscriptionDto(id, status, version, statement.path("purpose").asText());
  }
}
