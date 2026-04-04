package cloud.poesis.sie.operator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Validates payloads against archetype JSON schemas resolved from the mechanism topology. Used to
 * validate trigger payloads (receptor data) and effect payloads (effector data).
 */
@Service
public class PayloadValidatorService {

  private static final Logger log = LoggerFactory.getLogger(PayloadValidatorService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final JsonSchemaFactory schemaFactory;

  public PayloadValidatorService() {
    this.schemaFactory =
        JsonSchemaFactory.builder(JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7))
            .build();
  }

  /**
   * Validates a data payload against an archetype JSON Schema from the topology.
   *
   * @param archetypeName the archetype name (for error messages)
   * @param payload the data to validate
   * @param archetypeSchema the archetype statement (JSON Schema) from defman
   * @return validation result
   */
  public ValidationResult validate(
      String archetypeName, Map<String, Object> payload, JsonNode archetypeSchema) {
    JsonNode payloadNode = MAPPER.valueToTree(payload);
    return validate(archetypeName, payloadNode, archetypeSchema);
  }

  /**
   * Validates a data payload (as JsonNode) against an archetype JSON Schema.
   *
   * @param archetypeName the archetype name (for error messages)
   * @param payloadNode the data to validate
   * @param archetypeSchema the archetype statement (JSON Schema) from defman
   * @return validation result
   */
  public ValidationResult validate(
      String archetypeName, JsonNode payloadNode, JsonNode archetypeSchema) {
    JsonSchema schema = schemaFactory.getSchema(archetypeSchema);
    Set<ValidationMessage> errors = schema.validate(payloadNode);

    if (errors.isEmpty()) {
      log.debug("Payload valid against archetype '{}'", archetypeName);
      return ValidationResult.valid();
    }

    String errorMessages =
        errors.stream().map(ValidationMessage::getMessage).collect(Collectors.joining("; "));

    log.warn("Payload validation failed against archetype '{}': {}", archetypeName, errorMessages);
    return ValidationResult.invalid(archetypeName, errorMessages);
  }

  public record ValidationResult(boolean isValid, String archetypeName, String errors) {

    public static ValidationResult valid() {
      return new ValidationResult(true, null, null);
    }

    public static ValidationResult invalid(String archetypeName, String errors) {
      return new ValidationResult(false, archetypeName, errors);
    }
  }
}
