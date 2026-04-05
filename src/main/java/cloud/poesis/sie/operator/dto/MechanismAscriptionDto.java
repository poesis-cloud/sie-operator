package cloud.poesis.sie.operator.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.UUID;

public record MechanismAscriptionDto(
    UUID id, String status, int version, UUID structure, String function, String rule) {

  public MechanismAscriptionDto {
    Objects.requireNonNull(id, "MechanismAscriptionDto: id is required");
    Objects.requireNonNull(status, "MechanismAscriptionDto: status is required");
    Objects.requireNonNull(
        structure, "MechanismAscriptionDto: statement field 'structure' is required");
    if (function == null || function.isBlank()) {
      throw new IllegalArgumentException(
          "MechanismAscriptionDto: statement field 'function' is required");
    }
    if (rule == null || rule.isBlank()) {
      throw new IllegalArgumentException(
          "MechanismAscriptionDto: statement field 'rule' is required");
    }
  }

  @JsonCreator
  public static MechanismAscriptionDto fromJson(
      @JsonProperty("id") UUID id,
      @JsonProperty("status") String status,
      @JsonProperty("version") int version,
      @JsonProperty("statement") JsonNode statement) {
    Objects.requireNonNull(statement, "MechanismAscriptionDto: statement is required");
    String structureText = statement.path("structure").asText();
    if (structureText.isBlank()) {
      throw new IllegalArgumentException(
          "MechanismAscriptionDto: statement field 'structure' is required");
    }
    return new MechanismAscriptionDto(
        id,
        status,
        version,
        UUID.fromString(structureText),
        statement.path("function").asText(),
        statement.path("rule").asText());
  }
}
