package cloud.poesis.sie.operator.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record ArchetypeAscriptionDto(
    UUID id, String status, int version, String title, JsonNode schema) {

  @JsonCreator
  public static ArchetypeAscriptionDto fromJson(
      @JsonProperty("id") UUID id,
      @JsonProperty("status") String status,
      @JsonProperty("version") int version,
      @JsonProperty("statement") JsonNode statement) {
    return new ArchetypeAscriptionDto(
        id, status, version, statement.path("title").asText(), statement);
  }
}
