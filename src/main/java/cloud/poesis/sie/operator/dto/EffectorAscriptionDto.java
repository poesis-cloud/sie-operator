package cloud.poesis.sie.operator.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.UUID;

public record EffectorAscriptionDto(
    UUID id, String status, int version, UUID mechanism, UUID archetype) {

  public EffectorAscriptionDto {
    Objects.requireNonNull(id, "EffectorAscriptionDto: id is required");
    Objects.requireNonNull(status, "EffectorAscriptionDto: status is required");
    Objects.requireNonNull(
        mechanism, "EffectorAscriptionDto: statement field 'mechanism' is required");
    Objects.requireNonNull(
        archetype, "EffectorAscriptionDto: statement field 'archetype' is required");
  }

  @JsonCreator
  public static EffectorAscriptionDto fromJson(
      @JsonProperty("id") UUID id,
      @JsonProperty("status") String status,
      @JsonProperty("version") int version,
      @JsonProperty("statement") JsonNode statement) {
    Objects.requireNonNull(statement, "EffectorAscriptionDto: statement is required");
    String mechanismText = statement.path("mechanism").asText();
    if (mechanismText.isBlank()) {
      throw new IllegalArgumentException(
          "EffectorAscriptionDto: statement field 'mechanism' is required");
    }
    String archetypeText = statement.path("archetype").asText();
    if (archetypeText.isBlank()) {
      throw new IllegalArgumentException(
          "EffectorAscriptionDto: statement field 'archetype' is required");
    }
    return new EffectorAscriptionDto(
        id, status, version, UUID.fromString(mechanismText), UUID.fromString(archetypeText));
  }
}
