package cloud.poesis.sie.operator.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Objects;

public record ArchetypeBindingDto(String archetype, JsonNode value, JsonNode schema) {
  public ArchetypeBindingDto {
    if (archetype == null || archetype.isBlank()) {
      throw new IllegalArgumentException("ArchetypeBindingDto: archetype is required");
    }
    Objects.requireNonNull(value, "ArchetypeBindingDto: value is required");
    Objects.requireNonNull(schema, "ArchetypeBindingDto: schema is required");
  }
}
