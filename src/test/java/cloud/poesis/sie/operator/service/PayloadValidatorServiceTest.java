package cloud.poesis.sie.operator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PayloadValidatorServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private PayloadValidatorService validator;

  @BeforeEach
  void setUp() {
    validator = new PayloadValidatorService();
  }

  @Test
  void validPayloadPasses() {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("type", "object");
    schema.set("required", MAPPER.createArrayNode().add("ruleType").add("subjectType"));
    ObjectNode props = MAPPER.createObjectNode();
    props.set("ruleType", MAPPER.createObjectNode().put("type", "string"));
    props.set("subjectType", MAPPER.createObjectNode().put("type", "string"));
    schema.set("properties", props);

    Map<String, Object> payload = Map.of("ruleType", "test-rule", "subjectType", "DIRECTIVE");

    PayloadValidatorService.ValidationResult result =
        validator.validate("TestArchetype", payload, schema);

    assertThat(result.isValid()).isTrue();
    assertThat(result.errors()).isNull();
  }

  @Test
  void missingRequiredFieldFails() {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("type", "object");
    schema.set("required", MAPPER.createArrayNode().add("ruleType").add("subjectType"));
    ObjectNode props = MAPPER.createObjectNode();
    props.set("ruleType", MAPPER.createObjectNode().put("type", "string"));
    props.set("subjectType", MAPPER.createObjectNode().put("type", "string"));
    schema.set("properties", props);

    Map<String, Object> payload = Map.of("ruleType", "test-rule");

    PayloadValidatorService.ValidationResult result =
        validator.validate("TestArchetype", payload, schema);

    assertThat(result.isValid()).isFalse();
    assertThat(result.archetypeName()).isEqualTo("TestArchetype");
    assertThat(result.errors()).contains("subjectType");
  }

  @Test
  void wrongTypeFails() {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("type", "object");
    ObjectNode props = MAPPER.createObjectNode();
    props.set("count", MAPPER.createObjectNode().put("type", "integer"));
    schema.set("properties", props);

    Map<String, Object> payload = Map.of("count", "not-a-number");

    PayloadValidatorService.ValidationResult result =
        validator.validate("TestArchetype", payload, schema);

    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).isNotEmpty();
  }

  @Test
  void validPayloadWithJsonNodePasses() {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("type", "object");

    ObjectNode payloadNode = MAPPER.createObjectNode();
    payloadNode.put("key", "value");

    PayloadValidatorService.ValidationResult result =
        validator.validate("TestArchetype", payloadNode, schema);

    assertThat(result.isValid()).isTrue();
  }

  @Test
  void emptyPayloadPassesSchemaWithNoRequiredFields() {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("type", "object");

    Map<String, Object> payload = Map.of();

    PayloadValidatorService.ValidationResult result =
        validator.validate("TestArchetype", payload, schema);

    assertThat(result.isValid()).isTrue();
  }

  @Test
  void complexNestedSchemaValidation() {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("type", "object");
    schema.set("required", MAPPER.createArrayNode().add("subject"));
    ObjectNode props = MAPPER.createObjectNode();
    ObjectNode subjectProp = MAPPER.createObjectNode();
    subjectProp.put("type", "object");
    subjectProp.set("required", MAPPER.createArrayNode().add("modal"));
    ObjectNode subjectProps = MAPPER.createObjectNode();
    subjectProps.set("modal", MAPPER.createObjectNode().put("type", "string"));
    subjectProp.set("properties", subjectProps);
    props.set("subject", subjectProp);
    schema.set("properties", props);

    // Valid nested
    Map<String, Object> valid = Map.of("subject", Map.of("modal", "MUST"));
    assertThat(validator.validate("Test", valid, schema).isValid()).isTrue();

    // Missing nested required
    Map<String, Object> invalid = Map.of("subject", Map.of());
    assertThat(validator.validate("Test", invalid, schema).isValid()).isFalse();
  }
}
