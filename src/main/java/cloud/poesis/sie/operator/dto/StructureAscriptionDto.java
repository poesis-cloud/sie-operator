package cloud.poesis.sie.operator.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.UUID;

public record StructureAscriptionDto(UUID id, String status, int version, String purpose) {

  public StructureAscriptionDto {
    Objects.requireNonNull(id, "StructureAscriptionDto: id is required");
    Objects.requireNonNull(status, "StructureAscriptionDto: status is required");
    if (purpose == null || purpose.isBlank()) {
      throw new IllegalArgumentException(
          "StructureAscriptionDto: statement field 'purpose' is required");
    }
  }

  @JsonCreator
  public static StructureAscriptionDto fromJson(
      @JsonProperty("id") UUID id,
      @JsonProperty("status") String status,
      @JsonProperty("version") int version,
      @JsonProperty("statement") JsonNode statement) {
    Objects.requireNonNull(statement, "StructureAscriptionDto: statement is required");
    return new StructureAscriptionDto(id, status, version, statement.path("purpose").asText());
  }
}
