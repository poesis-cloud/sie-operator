package cloud.poesis.sie.operator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * Validates inputs against archetype JSON schemas resolved from the mechanism
 * frame. Used to
 * validate trigger inputs (receptor data) and effect outputs (effector data).
 */
@Service
public class OperationInputValidationService {

  private static final Logger log = LoggerFactory.getLogger(OperationInputValidationService.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final JsonSchemaFactory schemaFactory;

  public OperationInputValidationService() {
    this.schemaFactory = JsonSchemaFactory.builder(
        JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012))
        .build();
  }

  /**
   * Validates data against an archetype JSON Schema from the frame.
   *
   * @param archetypeName   the archetype name (for error messages)
   * @param data            the data to validate
   * @param archetypeSchema the archetype statement (JSON Schema) from defman
   * @return validation result
   */
  public ValidationResult validate(
      String archetypeName, Map<String, Object> data, JsonNode archetypeSchema) {
    JsonNode inputNode = MAPPER.valueToTree(data);
    return validate(archetypeName, inputNode, archetypeSchema);
  }

  /**
   * Validates data (as JsonNode) against an archetype JSON Schema.
   *
   * @param archetypeName   the archetype name (for error messages)
   * @param inputNode       the data to validate
   * @param archetypeSchema the archetype statement (JSON Schema) from defman
   * @return validation result
   */
  public ValidationResult validate(
      String archetypeName, JsonNode inputNode, JsonNode archetypeSchema) {
    JsonSchema schema = schemaFactory.getSchema(applyPayloadClosure(archetypeSchema));
    Set<ValidationMessage> errors = schema.validate(inputNode);

    if (errors.isEmpty()) {
      log.debug("Input valid against archetype '{}'", archetypeName);
      return ValidationResult.valid();
    }

    String errorMessages = errors.stream().map(ValidationMessage::getMessage).collect(Collectors.joining("; "));

    log.warn("Input validation failed against archetype '{}': {}", archetypeName, errorMessages);
    return ValidationResult.invalid(archetypeName, errorMessages);
  }

  /**
   * Returns the schema to validate a payload against: the data archetype schema
   * with {@code
   * unevaluatedProperties: false} applied at its root, so that a payload carries
   * only properties
   * declared somewhere in the resolved chain.
   *
   * <p>
   * Mirrors the closure defman applies to ascription statements — see GSM §5
   * ("Statement
   * closure"). Closure cannot be declared in the archetype schemas themselves
   * without defeating
   * their extensibility, so it is applied here, where the concrete data archetype
   * is known. The
   * input node is never mutated. Schemas already declaring an at-least-as-strict
   * top-level closure
   * are returned unchanged.
   */
  static JsonNode applyPayloadClosure(JsonNode archetypeSchema) {
    if (!archetypeSchema.isObject() || declaresClosure(archetypeSchema)) {
      return archetypeSchema;
    }
    ObjectNode closed = JsonNodeFactory.instance.objectNode();
    closed.setAll((ObjectNode) archetypeSchema);
    closed.put("unevaluatedProperties", false);
    return closed;
  }

  private static boolean declaresClosure(JsonNode schema) {
    return isFalse(schema.get("unevaluatedProperties"))
        || isFalse(schema.get("additionalProperties"));
  }

  private static boolean isFalse(JsonNode node) {
    return node != null && node.isBoolean() && !node.booleanValue();
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
