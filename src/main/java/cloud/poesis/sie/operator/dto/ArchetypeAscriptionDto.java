package cloud.poesis.sie.operator.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;
import java.util.UUID;

public record ArchetypeAscriptionDto(
    UUID id, String status, int version, String title, JsonNode schema) {

  public ArchetypeAscriptionDto {
    Objects.requireNonNull(id, "ArchetypeAscriptionDto: id is required");
    Objects.requireNonNull(status, "ArchetypeAscriptionDto: status is required");
    Objects.requireNonNull(schema, "ArchetypeAscriptionDto: statement is required");
    JsonNode uri = schema.get("$id");
    if (uri == null || !uri.isTextual() || uri.asText().isBlank()) {
      throw new IllegalArgumentException("ArchetypeAscriptionDto: statement root $id is required");
    }
  }

  @JsonCreator
  public static ArchetypeAscriptionDto fromJson(
      @JsonProperty("id") UUID id,
      @JsonProperty("status") String status,
      @JsonProperty("version") int version,
      @JsonProperty("statement") JsonNode statement) {
    Objects.requireNonNull(statement, "ArchetypeAscriptionDto: statement is required");
    return new ArchetypeAscriptionDto(
        id, status, version, statement.path("title").asText(), statement);
  }

  public String uri() {
    return schema.path("$id").asText();
  }
}
