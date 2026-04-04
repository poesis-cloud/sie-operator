package cloud.poesis.sie.operator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integration test exercising the full CP execution flow with ITIP governance artifacts
 * (DIRECTIVE_NORM_OPERATIONALIZATION mechanism) against a mocked Definition Manager.
 *
 * <p>Simulates the complete topology resolution: Mechanism → Receptor (AppraisalTrigger) → Effector
 * (AppraisalFinding) → Archetype schemas. Uses the real ITIP Starlark rule to verify both GAP and
 * COVERED appraisal cases end-to-end.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ItipGovernanceAppraisalIT {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  // Definition IDs (stable for deterministic mock routing)
  static final UUID MECHANISM_DEF_ID = UUID.fromString("00000001-0001-0001-0001-000000000001");
  static final UUID TRIGGER_ARCH_DEF_ID = UUID.fromString("00000001-0001-0001-0001-000000000002");
  static final UUID FINDING_ARCH_DEF_ID = UUID.fromString("00000001-0001-0001-0001-000000000003");
  static final UUID RECEPTOR_DEF_ID = UUID.fromString("00000001-0001-0001-0001-000000000004");
  static final UUID EFFECTOR_DEF_ID = UUID.fromString("00000001-0001-0001-0001-000000000005");
  static final UUID STRUCTURE_DEF_ID = UUID.fromString("00000001-0001-0001-0001-000000000006");

  // Ascription IDs
  static final UUID MECHANISM_ASC_ID = UUID.fromString("00000001-0001-0001-0001-000000000011");
  static final UUID TRIGGER_ARCH_ASC_ID = UUID.fromString("00000001-0001-0001-0001-000000000012");
  static final UUID FINDING_ARCH_ASC_ID = UUID.fromString("00000001-0001-0001-0001-000000000013");
  static final UUID RECEPTOR_ASC_ID = UUID.fromString("00000001-0001-0001-0001-000000000014");
  static final UUID EFFECTOR_ASC_ID = UUID.fromString("00000001-0001-0001-0001-000000000015");

  /**
   * The real ITIP Starlark rule for DIRECTIVE_NORM_OPERATIONALIZATION — copied verbatim from
   * itip/def/frameworks/itip/governance/DIRECTIVE_NORM_OPERATIONALIZATION.mechanism.ascription.statement.json
   */
  static final String STARLARK_RULE =
      """
      evt = sys.receive("AppraisalTrigger")
      directive = evt["subject"]
      norms = evt["relatedAscriptions"]

      if len(norms) == 0:
          modal = directive["modal"]
          verb = directive["verb"]
          purpose = directive["purpose"]
          sys.effect("AppraisalFinding", {
              "ruleType": evt["ruleType"],
              "findingType": "GAP",
              "subjectType": evt["subjectType"],
              "subjectDefinitionId": evt["subjectDefinitionId"],
              "severity": "HIGH",
              "message": modal + " " + verb + " on '" + purpose + "' has no operationalizing Norms \
      — governance intent is not translated into operational rules",
          })
      """;

  static MockWebServer defmanMock;

  @Autowired TestRestTemplate restTemplate;

  @DynamicPropertySource
  static void overrideDefmanUrl(DynamicPropertyRegistry registry) {
    registry.add("op.definition-manager.url", () -> "http://localhost:" + defmanMock.getPort());
  }

  @BeforeAll
  static void startMockDefman() throws IOException {
    defmanMock = new MockWebServer();
    defmanMock.setDispatcher(new DefmanDispatcher());
    defmanMock.start();
  }

  @AfterAll
  static void stopMockDefman() throws IOException {
    defmanMock.shutdown();
  }

  // ---------- GAP case: Directive with no norms → AppraisalFinding(GAP, HIGH) ----------

  @Test
  void gapCase_directiveWithNoNorms_producesHighSeverityFinding() {
    UUID directiveDefId = UUID.randomUUID();
    UUID qualifierDefId = UUID.randomUUID();
    UUID purposeDefId = UUID.randomUUID();

    Map<String, Object> trigger =
        Map.of(
            "ruleType", "gsm:rules/appraisal/directive/norm/operationalization",
            "subjectType", "DIRECTIVE",
            "subjectDefinitionId", directiveDefId.toString(),
            "subject",
                Map.of(
                    "structure", STRUCTURE_DEF_ID.toString(),
                    "modal", "MUST",
                    "verb", "ENSURE",
                    "qualifier", qualifierDefId.toString(),
                    "purpose", purposeDefId.toString()),
            "relatedAscriptions", List.of());

    Map<String, Object> body =
        Map.of("mechanismAscriptionId", MECHANISM_ASC_ID.toString(), "triggerPayload", trigger);

    var response = restTemplate.postForEntity("/api/v1/operations", body, JsonNode.class);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

    JsonNode responseBody = response.getBody();
    assertThat(responseBody).isNotNull();
    assertThat(responseBody.path("success").asBoolean()).isTrue();

    JsonNode effects = responseBody.path("effects");
    assertThat(effects.isArray()).isTrue();
    assertThat(effects).hasSize(1);

    JsonNode finding = effects.get(0);
    assertThat(finding.path("archetype").asText()).isEqualTo("AppraisalFinding");

    JsonNode data = finding.path("data");
    assertThat(data.path("findingType").asText()).isEqualTo("GAP");
    assertThat(data.path("severity").asText()).isEqualTo("HIGH");
    assertThat(data.path("subjectType").asText()).isEqualTo("DIRECTIVE");
    assertThat(data.path("subjectDefinitionId").asText()).isEqualTo(directiveDefId.toString());
    assertThat(data.path("ruleType").asText())
        .isEqualTo("gsm:rules/appraisal/directive/norm/operationalization");
    assertThat(data.path("message").asText()).contains("no operationalizing Norms");
  }

  // ---------- COVERED case: Directive with norms → no findings ----------

  @Test
  void coveredCase_directiveWithNorms_producesNoFindings() {
    UUID directiveDefId = UUID.randomUUID();
    UUID qualifierDefId = UUID.randomUUID();
    UUID purposeDefId = UUID.randomUUID();

    Map<String, Object> trigger =
        Map.of(
            "ruleType", "gsm:rules/appraisal/directive/norm/operationalization",
            "subjectType", "DIRECTIVE",
            "subjectDefinitionId", directiveDefId.toString(),
            "subject",
                Map.of(
                    "structure", STRUCTURE_DEF_ID.toString(),
                    "modal", "MUST",
                    "verb", "ENSURE",
                    "qualifier", qualifierDefId.toString(),
                    "purpose", purposeDefId.toString()),
            "relatedAscriptions",
                List.of(
                    Map.of(
                        "structure",
                        purposeDefId.toString(),
                        "qualifier",
                        qualifierDefId.toString(),
                        "applicability",
                        "true",
                        "assertion",
                        "true")));

    Map<String, Object> body =
        Map.of("mechanismAscriptionId", MECHANISM_ASC_ID.toString(), "triggerPayload", trigger);

    var response = restTemplate.postForEntity("/api/v1/operations", body, JsonNode.class);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

    JsonNode responseBody = response.getBody();
    assertThat(responseBody).isNotNull();
    assertThat(responseBody.path("success").asBoolean()).isTrue();

    JsonNode effects = responseBody.path("effects");
    assertThat(effects.isArray()).isTrue();
    assertThat(effects).isEmpty();
  }

  // ---------- Validation case: invalid trigger payload → validation failure ----------

  @Test
  void invalidTrigger_missingRequiredFields_returnsValidationError() {
    Map<String, Object> invalidPayload = Map.of("unknownField", "value");

    Map<String, Object> body =
        Map.of(
            "mechanismAscriptionId", MECHANISM_ASC_ID.toString(), "triggerPayload", invalidPayload);

    var response = restTemplate.postForEntity("/api/v1/operations", body, JsonNode.class);

    assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

    JsonNode responseBody = response.getBody();
    assertThat(responseBody).isNotNull();
    assertThat(responseBody.path("success").asBoolean()).isFalse();
    assertThat(responseBody.path("error").asText()).contains("validation failed");
  }

  // ==================== Mock Definition Manager Dispatcher ====================

  static class DefmanDispatcher extends Dispatcher {

    @Override
    public MockResponse dispatch(RecordedRequest request) {
      String path = request.getPath();
      if (path == null) {
        return notFound();
      }

      try {
        // GET /api/v1/ascriptions/{id} — direct fetch by ascription ID
        if (path.startsWith("/api/v1/ascriptions/") && !path.contains("?")) {
          return dispatchAscriptionById(path);
        }
        // GET /api/v1/ascriptions?type=...&statement.mechanism=...
        if (path.startsWith("/api/v1/ascriptions?") || path.startsWith("/api/v1/ascriptions%3F")) {
          return dispatchAscriptionQuery(path);
        }
      } catch (Exception e) {
        return new MockResponse().setResponseCode(500).setBody(e.getMessage());
      }

      return notFound();
    }

    private MockResponse dispatchAscriptionById(String path) throws Exception {
      String ascIdStr = path.replace("/api/v1/ascriptions/", "");
      UUID ascId = UUID.fromString(ascIdStr);

      if (MECHANISM_ASC_ID.equals(ascId)) {
        return jsonResponse(mechanismAscription());
      }
      if (TRIGGER_ARCH_ASC_ID.equals(ascId)) {
        return jsonResponse(triggerArchetypeAscription());
      }
      if (FINDING_ARCH_ASC_ID.equals(ascId)) {
        return jsonResponse(findingArchetypeAscription());
      }
      return notFound();
    }

    private MockResponse dispatchAscriptionQuery(String path) throws Exception {
      if (path.contains("type=RECEPTOR")) {
        return halResponse(List.of(receptorAscription()));
      } else if (path.contains("type=EFFECTOR")) {
        return halResponse(List.of(effectorAscription()));
      }
      return halResponse(List.of());
    }

    // -- Mechanism ascription (DIRECTIVE_NORM_OPERATIONALIZATION) --

    private static JsonNode mechanismAscription() {
      ObjectNode asc = MAPPER.createObjectNode();
      asc.put("id", MECHANISM_ASC_ID.toString());
      asc.put("definitionId", MECHANISM_DEF_ID.toString());
      asc.put("version", 1);
      asc.put("status", "ACTIVE");

      ObjectNode stmt = MAPPER.createObjectNode();
      stmt.put("structure", STRUCTURE_DEF_ID.toString());
      stmt.put("function", "DIRECTIVE_NORM_OPERATIONALIZATION");
      stmt.put("rule", STARLARK_RULE);
      asc.set("statement", stmt);

      return asc;
    }

    // -- Receptor ascription (AppraisalTrigger port) --

    private static JsonNode receptorAscription() {
      ObjectNode asc = MAPPER.createObjectNode();
      asc.put("id", RECEPTOR_ASC_ID.toString());
      asc.put("definitionId", RECEPTOR_DEF_ID.toString());
      asc.put("version", 1);
      asc.put("status", "ACTIVE");

      ObjectNode stmt = MAPPER.createObjectNode();
      stmt.put("mechanism", MECHANISM_ASC_ID.toString());
      stmt.put("archetype", TRIGGER_ARCH_ASC_ID.toString());
      asc.set("statement", stmt);

      return asc;
    }

    // -- Effector ascription (AppraisalFinding port) --

    private static JsonNode effectorAscription() {
      ObjectNode asc = MAPPER.createObjectNode();
      asc.put("id", EFFECTOR_ASC_ID.toString());
      asc.put("definitionId", EFFECTOR_DEF_ID.toString());
      asc.put("version", 1);
      asc.put("status", "ACTIVE");

      ObjectNode stmt = MAPPER.createObjectNode();
      stmt.put("mechanism", MECHANISM_ASC_ID.toString());
      stmt.put("archetype", FINDING_ARCH_ASC_ID.toString());
      asc.set("statement", stmt);

      return asc;
    }

    // -- AppraisalTrigger archetype ascription (data archetype for receptor) --

    private static JsonNode triggerArchetypeAscription() {
      ObjectNode asc = MAPPER.createObjectNode();
      asc.put("id", TRIGGER_ARCH_ASC_ID.toString());
      asc.put("definitionId", TRIGGER_ARCH_DEF_ID.toString());
      asc.put("version", 1);
      asc.put("status", "ACTIVE");

      // Statement IS the archetype schema — matches ITIP's AppraisalTrigger.Archetype
      ObjectNode schema = MAPPER.createObjectNode();
      schema.put("title", "AppraisalTrigger");
      schema.put("type", "object");
      ArrayNode required = MAPPER.createArrayNode();
      required.add("ruleType").add("subjectType").add("subjectDefinitionId").add("subject");
      schema.set("required", required);

      ObjectNode props = MAPPER.createObjectNode();
      props.set("ruleType", MAPPER.createObjectNode().put("type", "string"));
      props.set("subjectType", MAPPER.createObjectNode().put("type", "string"));
      props.set(
          "subjectDefinitionId",
          MAPPER.createObjectNode().put("type", "string").put("format", "uuid"));
      props.set("subject", MAPPER.createObjectNode().put("type", "object"));
      ObjectNode relatedAsc = MAPPER.createObjectNode();
      relatedAsc.put("type", "array");
      relatedAsc.set("items", MAPPER.createObjectNode().put("type", "object"));
      props.set("relatedAscriptions", relatedAsc);
      schema.set("properties", props);

      asc.set("statement", schema);
      return asc;
    }

    // -- AppraisalFinding archetype ascription (data archetype for effector) --

    private static JsonNode findingArchetypeAscription() {
      ObjectNode asc = MAPPER.createObjectNode();
      asc.put("id", FINDING_ARCH_ASC_ID.toString());
      asc.put("definitionId", FINDING_ARCH_DEF_ID.toString());
      asc.put("version", 1);
      asc.put("status", "ACTIVE");

      // Statement IS the archetype schema — matches ITIP's AppraisalFinding.Archetype
      ObjectNode schema = MAPPER.createObjectNode();
      schema.put("title", "AppraisalFinding");
      schema.put("type", "object");
      ArrayNode required = MAPPER.createArrayNode();
      required
          .add("ruleType")
          .add("findingType")
          .add("subjectType")
          .add("subjectDefinitionId")
          .add("severity")
          .add("message");
      schema.set("required", required);

      ObjectNode props = MAPPER.createObjectNode();
      props.set("ruleType", MAPPER.createObjectNode().put("type", "string"));
      props.set("findingType", MAPPER.createObjectNode().put("type", "string"));
      props.set("subjectType", MAPPER.createObjectNode().put("type", "string"));
      props.set(
          "subjectDefinitionId",
          MAPPER.createObjectNode().put("type", "string").put("format", "uuid"));
      props.set("severity", MAPPER.createObjectNode().put("type", "string"));
      props.set("message", MAPPER.createObjectNode().put("type", "string"));
      schema.set("properties", props);

      asc.set("statement", schema);
      return asc;
    }

    // -- HAL JSON response wrapper --

    private static MockResponse halResponse(List<JsonNode> ascriptions) throws Exception {
      ObjectNode body = MAPPER.createObjectNode();
      ObjectNode embedded = MAPPER.createObjectNode();
      ArrayNode list = MAPPER.createArrayNode();
      ascriptions.forEach(list::add);
      embedded.set("ascriptionDtoList", list);
      body.set("_embedded", embedded);

      ObjectNode page = MAPPER.createObjectNode();
      page.put("size", 20);
      page.put("totalElements", ascriptions.size());
      page.put("totalPages", 1);
      page.put("number", 0);
      body.set("page", page);

      return jsonResponse(body);
    }

    private static MockResponse jsonResponse(JsonNode body) throws Exception {
      return new MockResponse()
          .setHeader("Content-Type", "application/json")
          .setBody(MAPPER.writeValueAsString(body));
    }

    private static MockResponse notFound() {
      return new MockResponse().setResponseCode(404);
    }
  }
}
