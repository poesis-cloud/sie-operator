package cloud.poesis.sie.operator.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.UUID;

public record ReceptorAscriptionDto(
    UUID id,
    String status,
    int version,
    String portArchetypeUri,
    UUID mechanism,
    String archetype) {

  public ReceptorAscriptionDto {
    Objects.requireNonNull(id, "ReceptorAscriptionDto: id is required");
    Objects.requireNonNull(status, "ReceptorAscriptionDto: status is required");
    if (portArchetypeUri == null || portArchetypeUri.isBlank()) {
      throw new IllegalArgumentException("ReceptorAscriptionDto: port Archetype URI is required");
    }
    Objects.requireNonNull(
        mechanism, "ReceptorAscriptionDto: statement field 'mechanism' is required");
    if (archetype == null || archetype.isBlank()) {
      throw new IllegalArgumentException(
          "ReceptorAscriptionDto: statement field 'archetype' is required");
    }
  }

  public static ReceptorAscriptionDto fromJson(
      UUID id, String status, int version, String portArchetypeUri, JsonNode statement) {
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
        id, status, version, portArchetypeUri, UUID.fromString(mechanismText), archetypeText);
  }
}
