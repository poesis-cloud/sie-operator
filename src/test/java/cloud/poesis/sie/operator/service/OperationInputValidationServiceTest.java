package cloud.poesis.sie.operator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OperationInputValidationServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private OperationInputValidationService validator;

  @BeforeEach
  void setUp() {
    validator = new OperationInputValidationService();
  }

  @Test
  void validInputPasses() {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("type", "object");
    schema.set("required", MAPPER.createArrayNode().add("ruleType").add("subjectType"));
    ObjectNode props = MAPPER.createObjectNode();
    props.set("ruleType", MAPPER.createObjectNode().put("type", "string"));
    props.set("subjectType", MAPPER.createObjectNode().put("type", "string"));
    schema.set("properties", props);

    Map<String, Object> input = Map.of("ruleType", "test-rule", "subjectType", "DIRECTIVE");

    OperationInputValidationService.ValidationResult result =
        validator.validate("TestArchetype", input, schema);

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

    Map<String, Object> input = Map.of("ruleType", "test-rule");

    OperationInputValidationService.ValidationResult result =
        validator.validate("TestArchetype", input, schema);

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

    Map<String, Object> input = Map.of("count", "not-a-number");

    OperationInputValidationService.ValidationResult result =
        validator.validate("TestArchetype", input, schema);

    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).isNotEmpty();
  }

  @Test
  void validInputWithJsonNodePasses() {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("type", "object");

    ObjectNode inputNode = MAPPER.createObjectNode();
    inputNode.put("key", "value");

    OperationInputValidationService.ValidationResult result =
        validator.validate("TestArchetype", inputNode, schema);

    assertThat(result.isValid()).isTrue();
  }

  @Test
  void emptyInputPassesSchemaWithNoRequiredFields() {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("type", "object");

    Map<String, Object> input = Map.of();

    OperationInputValidationService.ValidationResult result =
        validator.validate("TestArchetype", input, schema);

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

  // --- HTTP schema coherence tests (loaded from classpath) ---

  @Test
  void httpRequestSchemaAcceptsGetWithOnlyMethodAndTargetUri() throws IOException {
    JsonNode schema = loadSchema("statement/protocol/http/HttpRequest.schema.json");

    Map<String, Object> getRequest = Map.of("method", "GET", "targetUri", "/api/items");

    OperationInputValidationService.ValidationResult result =
        validator.validate("HttpRequest", getRequest, schema);

    assertThat(result.isValid())
        .as("GET request with only method + targetUri must pass HttpRequest schema")
        .isTrue();
  }

  @Test
  void httpRequestSchemaAcceptsPostWithBody() throws IOException {
    JsonNode schema = loadSchema("statement/protocol/http/HttpRequest.schema.json");

    Map<String, Object> postRequest =
        Map.of(
            "method", "POST",
            "targetUri", "/api/orders",
            "contentType", "application/json",
            "body", Map.of("orderId", "ORD-001"));

    OperationInputValidationService.ValidationResult result =
        validator.validate("HttpRequest", postRequest, schema);

    assertThat(result.isValid())
        .as("POST request with method, targetUri, contentType, and body must pass")
        .isTrue();
  }

  @Test
  void httpRequestSchemaRejectsWithoutMethod() throws IOException {
    JsonNode schema = loadSchema("statement/protocol/http/HttpRequest.schema.json");

    Map<String, Object> noMethod = Map.of("targetUri", "/api/items");

    OperationInputValidationService.ValidationResult result =
        validator.validate("HttpRequest", noMethod, schema);

    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).contains("method");
  }

  @Test
  void httpResponseSchemaAcceptsNoContentWithOnlyStatusCode() throws IOException {
    JsonNode schema = loadSchema("statement/protocol/http/HttpResponse.schema.json");

    Map<String, Object> noContent = Map.of("statusCode", 204);

    OperationInputValidationService.ValidationResult result =
        validator.validate("HttpResponse", noContent, schema);

    assertThat(result.isValid())
        .as("204 No Content with only statusCode must pass HttpResponse schema")
        .isTrue();
  }

  @Test
  void httpResponseSchemaAcceptsFullResponse() throws IOException {
    JsonNode schema = loadSchema("statement/protocol/http/HttpResponse.schema.json");

    Map<String, Object> fullResponse =
        Map.of("statusCode", 200, "contentType", "application/json", "body", "{\"ok\":true}");

    OperationInputValidationService.ValidationResult result =
        validator.validate("HttpResponse", fullResponse, schema);

    assertThat(result.isValid())
        .as("200 response with statusCode, contentType, and body must pass")
        .isTrue();
  }

  @Test
  void httpResponseSchemaRejectsWithoutStatusCode() throws IOException {
    JsonNode schema = loadSchema("statement/protocol/http/HttpResponse.schema.json");

    Map<String, Object> noStatus = Map.of("body", "some body");

    OperationInputValidationService.ValidationResult result =
        validator.validate("HttpResponse", noStatus, schema);

    assertThat(result.isValid()).isFalse();
    assertThat(result.errors()).contains("statusCode");
  }

  private JsonNode loadSchema(String classPathResource) throws IOException {
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(classPathResource)) {
      assertThat(is).as("Schema not found on classpath: " + classPathResource).isNotNull();
      ObjectNode schema = (ObjectNode) MAPPER.readTree(is);
      // Strip GSM-level annotations that are not JSON Schema meta-schema pointers;
      // in production, archetype schemas arrive from defman without these fields.
      schema.remove("$schema");
      schema.remove("$id");
      return schema;
    }
  }
}
