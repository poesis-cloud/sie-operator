package cloud.poesis.sie.operator.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.UUID;

public record NormAscriptionDto(UUID id, String status, int version, String applicability) {
  public NormAscriptionDto {
    Objects.requireNonNull(id, "NormAscriptionDto: id is required");
    Objects.requireNonNull(status, "NormAscriptionDto: status is required");
    Objects.requireNonNull(applicability, "NormAscriptionDto: applicability is required");
  }

  @JsonCreator
  public static NormAscriptionDto fromJson(
      @JsonProperty("id") UUID id,
      @JsonProperty("status") String status,
      @JsonProperty("version") int version,
      @JsonProperty("statement") JsonNode statement) {
    Objects.requireNonNull(statement, "NormAscriptionDto: statement is required");
    return new NormAscriptionDto(
        id, status, version, statement.path("applicability").asText("true"));
  }
}
