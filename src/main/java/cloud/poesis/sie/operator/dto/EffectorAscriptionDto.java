package cloud.poesis.sie.operator.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.UUID;

public record EffectorAscriptionDto(
    UUID id,
    String status,
    int version,
    String portArchetypeUri,
    UUID mechanism,
    String archetype) {

  public EffectorAscriptionDto {
    Objects.requireNonNull(id, "EffectorAscriptionDto: id is required");
    Objects.requireNonNull(status, "EffectorAscriptionDto: status is required");
    if (portArchetypeUri == null || portArchetypeUri.isBlank()) {
      throw new IllegalArgumentException("EffectorAscriptionDto: port Archetype URI is required");
    }
    Objects.requireNonNull(
        mechanism, "EffectorAscriptionDto: statement field 'mechanism' is required");
    if (archetype == null || archetype.isBlank()) {
      throw new IllegalArgumentException(
          "EffectorAscriptionDto: statement field 'archetype' is required");
    }
  }

  public static EffectorAscriptionDto fromJson(
      UUID id, String status, int version, String portArchetypeUri, JsonNode statement) {
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
        id, status, version, portArchetypeUri, UUID.fromString(mechanismText), archetypeText);
  }
}
