package cloud.poesis.sie.operator.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.UUID;

public record ReceptorAscriptionDto(
    UUID id, String status, int version, UUID mechanism, UUID archetype) {

  public ReceptorAscriptionDto {
    Objects.requireNonNull(id, "ReceptorAscriptionDto: id is required");
    Objects.requireNonNull(status, "ReceptorAscriptionDto: status is required");
    Objects.requireNonNull(
        mechanism, "ReceptorAscriptionDto: statement field 'mechanism' is required");
    Objects.requireNonNull(
        archetype, "ReceptorAscriptionDto: statement field 'archetype' is required");
  }

  @JsonCreator
  public static ReceptorAscriptionDto fromJson(
      @JsonProperty("id") UUID id,
      @JsonProperty("status") String status,
      @JsonProperty("version") int version,
      @JsonProperty("statement") JsonNode statement) {
    Objects.requireNonNull(statement, "ReceptorAscriptionDto: statement is required");
    String mechanismText = statement.path("mechanism").asText();
    if (mechanismText.isBlank()) {
      throw new IllegalArgumentException(
          "ReceptorAscriptionDto: statement field 'mechanism' is required");
    }
    String archetypeText = statement.path("archetype").asText();
    if (archetypeText.isBlank()) {
      throw new IllegalArgumentException(
          "ReceptorAscriptionDto: statement field 'archetype' is required");
    }
    return new ReceptorAscriptionDto(
        id, status, version, UUID.fromString(mechanismText), UUID.fromString(archetypeText));
  }
}
