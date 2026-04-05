package cloud.poesis.sie.operator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cloud.poesis.sie.operator.dto.ArchetypeAscriptionDto;
import cloud.poesis.sie.operator.dto.EffectDto;
import cloud.poesis.sie.operator.dto.MechanismAscriptionDto;
import cloud.poesis.sie.operator.dto.OperationRequestDto;
import cloud.poesis.sie.operator.dto.OperationResponseDto;
import cloud.poesis.sie.operator.dto.OperationTopologyDto;
import cloud.poesis.sie.operator.exception.OperationTopologyResolutionException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OperationServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock private OperationTopologyResolutionService topologyResolver;

  private PayloadValidatorService payloadValidator;
  private OperationExecutionService sandbox;
  private OperationService operationService;

  @BeforeEach
  void setUp() {
    payloadValidator = new PayloadValidatorService();
    sandbox = new OperationExecutionService();
    List<EffectDispatchService> dispatchers = List.of(new LoggingEffectDispatchService());
    operationService =
        new OperationService(topologyResolver, payloadValidator, sandbox, dispatchers);
  }

  @Test
  void executesFireAndForgetMechanism() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("OrderCreated")
        sys.effect("OrderConfirmation", {"orderId": event["orderId"]})
        """;

    OperationTopologyDto topology = topologyWithNoSchemas(mechanismAscId, ruleSource);
    when(topologyResolver.resolve(mechanismAscId)).thenReturn(topology);

    OperationRequestDto request =
        new OperationRequestDto(mechanismAscId, Map.of("orderId", "ORD-001"));

    OperationResponseDto response = operationService.operate(request);

    assertThat(response.success()).isTrue();
    assertThat(response.effects()).hasSize(1);
    assertThat(response.effects().getFirst().archetype()).isEqualTo("OrderConfirmation");
    assertThat(response.effects().getFirst().data()).containsEntry("orderId", "ORD-001");
  }

  @Test
  void executesWithTopologyValidation() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("AppraisalTrigger")
        if len(event["relatedAscriptions"]) == 0:
            sys.effect("AppraisalFinding", {
                "ruleType": event["ruleType"],
                "findingType": "GAP",
                "subjectType": event["subjectType"],
                "subjectDefinitionId": event["subjectDefinitionId"],
                "severity": "HIGH",
                "message": "No norms found"
            })
        """;

    JsonNode triggerSchema = triggerArchetypeSchema();
    JsonNode findingSchema = findingArchetypeSchema();

    UUID triggerArchId = UUID.randomUUID();
    UUID findingArchId = UUID.randomUUID();

    ArchetypeAscriptionDto triggerArchetype =
        new ArchetypeAscriptionDto(triggerArchId, "ACTIVE", 1, "AppraisalTrigger", triggerSchema);
    ArchetypeAscriptionDto findingArchetype =
        new ArchetypeAscriptionDto(findingArchId, "ACTIVE", 1, "AppraisalFinding", findingSchema);

    OperationTopologyDto.ResolvedPort receptor =
        new OperationTopologyDto.ResolvedPort(
            UUID.randomUUID(), triggerArchId, "AppraisalTrigger", triggerSchema);
    OperationTopologyDto.ResolvedPort effector =
        new OperationTopologyDto.ResolvedPort(
            UUID.randomUUID(), findingArchId, "AppraisalFinding", findingSchema);

    OperationTopologyDto topology =
        new OperationTopologyDto(
            mechanismAscId,
            mechanismAscription(mechanismAscId, ruleSource),
            List.of(receptor),
            List.of(effector),
            Map.of("AppraisalTrigger", triggerArchetype, "AppraisalFinding", findingArchetype));
    when(topologyResolver.resolve(mechanismAscId)).thenReturn(topology);

    UUID subjectDefId = UUID.randomUUID();
    Map<String, Object> triggerPayload =
        Map.of(
            "ruleType", "gsm:rules/appraisal/directive/norm/operationalization",
            "subjectType", "DIRECTIVE",
            "subjectDefinitionId", subjectDefId.toString(),
            "subject", Map.of("modal", "MUST", "verb", "protect", "purpose", "sec"),
            "relatedAscriptions", List.of());

    OperationRequestDto request = new OperationRequestDto(mechanismAscId, triggerPayload);

    OperationResponseDto response = operationService.operate(request);

    assertThat(response.success()).isTrue();
    assertThat(response.effects()).hasSize(1);
    assertThat(response.effects().getFirst().archetype()).isEqualTo("AppraisalFinding");
    assertThat(response.effects().getFirst().data()).containsEntry("findingType", "GAP");
  }

  @Test
  void rejectsTriggerPayloadFailingSchemaValidation() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource = "event = sys.receive(\"AppraisalTrigger\")";

    JsonNode triggerSchema = triggerArchetypeSchema();
    UUID triggerArchId = UUID.randomUUID();

    ArchetypeAscriptionDto triggerArchetype =
        new ArchetypeAscriptionDto(triggerArchId, "ACTIVE", 1, "AppraisalTrigger", triggerSchema);

    OperationTopologyDto.ResolvedPort receptor =
        new OperationTopologyDto.ResolvedPort(
            UUID.randomUUID(), triggerArchId, "AppraisalTrigger", triggerSchema);

    OperationTopologyDto topology =
        new OperationTopologyDto(
            mechanismAscId,
            mechanismAscription(mechanismAscId, ruleSource),
            List.of(receptor),
            List.of(),
            Map.of("AppraisalTrigger", triggerArchetype));
    when(topologyResolver.resolve(mechanismAscId)).thenReturn(topology);

    // Missing required fields — should fail validation
    Map<String, Object> badPayload = Map.of("unknownField", "value");

    OperationRequestDto request = new OperationRequestDto(mechanismAscId, badPayload);

    OperationResponseDto response = operationService.operate(request);

    assertThat(response.success()).isFalse();
    assertThat(response.error()).contains("validation failed");
  }

  @Test
  void executesClosedLoopWithHandler() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("OrderCreated")
        result = sys.effect("ValidatePayment", {"amount": event["amount"]}).receive("PaymentResult")
        if result["valid"]:
            sys.effect("OrderApproved", {"orderId": event["orderId"]})
        """;

    OperationTopologyDto topology = topologyWithNoSchemas(mechanismAscId, ruleSource);
    when(topologyResolver.resolve(mechanismAscId)).thenReturn(topology);

    OperationRequestDto request =
        new OperationRequestDto(mechanismAscId, Map.of("orderId", "ORD-002", "amount", 100));

    OperationResponseDto response = operationService.operate(request);

    assertThat(response).isNotNull();
  }

  @Test
  void returnsFailureWhenRuleHasSyntaxError() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource = "event = sys.receive(\"T\")\nif True\n  pass";

    OperationTopologyDto topology = topologyWithNoSchemas(mechanismAscId, ruleSource);
    when(topologyResolver.resolve(mechanismAscId)).thenReturn(topology);

    OperationRequestDto request = new OperationRequestDto(mechanismAscId, Map.of());

    OperationResponseDto response = operationService.operate(request);

    assertThat(response.success()).isFalse();
    assertThat(response.error()).isNotEmpty();
  }

  @Test
  void returnsFailureWhenMechanismNotFound() {
    UUID mechanismAscId = UUID.randomUUID();
    when(topologyResolver.resolve(mechanismAscId))
        .thenThrow(
            new OperationTopologyResolutionException(
                "Mechanism ascription not found: " + mechanismAscId));

    OperationRequestDto request = new OperationRequestDto(mechanismAscId, Map.of());

    OperationResponseDto response = operationService.operate(request);

    assertThat(response.success()).isFalse();
    assertThat(response.error()).contains("not found");
  }

  @Test
  void returnsFailureWhenMechanismHasNoRuleSource() {
    UUID mechanismAscId = UUID.randomUUID();
    when(topologyResolver.resolve(mechanismAscId))
        .thenThrow(
            new OperationTopologyResolutionException("Mechanism ascription has no rule: test-id"));

    OperationRequestDto request = new OperationRequestDto(mechanismAscId, Map.of());

    OperationResponseDto response = operationService.operate(request);

    assertThat(response.success()).isFalse();
    assertThat(response.error()).contains("rule");
  }

  @Test
  void failsWhenClosedLoopResponseFailsSchemaValidation() {
    UUID mechanismAscId = UUID.randomUUID();
    String ruleSource =
        """
        event = sys.receive("Trigger")
        result = sys.effect("Request", {"id": "123"}).receive("Response")
        val = result["value"]
        """;

    JsonNode responseSchema = responseArchetypeSchema();

    ArchetypeAscriptionDto responseArchetype =
        new ArchetypeAscriptionDto(UUID.randomUUID(), "ACTIVE", 1, "Response", responseSchema);

    OperationTopologyDto topology =
        new OperationTopologyDto(
            mechanismAscId,
            mechanismAscription(mechanismAscId, ruleSource),
            List.of(),
            List.of(),
            Map.of("Response", responseArchetype));
    when(topologyResolver.resolve(mechanismAscId)).thenReturn(topology);

    // Dispatcher returns a map missing the required "value" field
    EffectDispatchService invalidResponseDispatcher =
        new EffectDispatchService() {
          @Override
          public boolean supports(EffectDto effect) {
            return true;
          }

          @Override
          public Map<String, Object> dispatch(EffectDto effect) {
            return Map.of("wrongField", 42);
          }
        };
    OperationService service =
        new OperationService(
            topologyResolver, payloadValidator, sandbox, List.of(invalidResponseDispatcher));

    OperationRequestDto request = new OperationRequestDto(mechanismAscId, Map.of());

    OperationResponseDto response = service.operate(request);

    assertThat(response.success()).isFalse();
    assertThat(response.error()).contains("Closed-loop response validation failed");
  }

  // --- Topology helpers ---

  private OperationTopologyDto topologyWithNoSchemas(UUID ascId, String ruleSource) {
    return new OperationTopologyDto(
        ascId,
        mechanismAscription(ascId, ruleSource),
        List.of(),
        List.of(),
        Collections.emptyMap());
  }

  private MechanismAscriptionDto mechanismAscription(UUID ascId, String ruleSource) {
    return new MechanismAscriptionDto(ascId, "ACTIVE", 1, UUID.randomUUID(), "test", ruleSource);
  }

  private JsonNode triggerArchetypeSchema() {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("type", "object");
    schema.set(
        "required",
        MAPPER
            .createArrayNode()
            .add("ruleType")
            .add("subjectType")
            .add("subjectDefinitionId")
            .add("subject"));
    ObjectNode props = MAPPER.createObjectNode();
    props.set("ruleType", MAPPER.createObjectNode().put("type", "string"));
    props.set("subjectType", MAPPER.createObjectNode().put("type", "string"));
    props.set("subjectDefinitionId", MAPPER.createObjectNode().put("type", "string"));
    props.set("subject", MAPPER.createObjectNode().put("type", "object"));
    props.set("relatedAscriptions", MAPPER.createObjectNode().put("type", "array"));
    schema.set("properties", props);
    return schema;
  }

  private JsonNode findingArchetypeSchema() {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("type", "object");
    schema.set(
        "required",
        MAPPER
            .createArrayNode()
            .add("ruleType")
            .add("findingType")
            .add("subjectType")
            .add("subjectDefinitionId")
            .add("severity")
            .add("message"));
    ObjectNode props = MAPPER.createObjectNode();
    props.set("ruleType", MAPPER.createObjectNode().put("type", "string"));
    props.set("findingType", MAPPER.createObjectNode().put("type", "string"));
    props.set("subjectType", MAPPER.createObjectNode().put("type", "string"));
    props.set("subjectDefinitionId", MAPPER.createObjectNode().put("type", "string"));
    props.set("severity", MAPPER.createObjectNode().put("type", "string"));
    props.set("message", MAPPER.createObjectNode().put("type", "string"));
    schema.set("properties", props);
    return schema;
  }

  private JsonNode responseArchetypeSchema() {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("type", "object");
    schema.set("required", MAPPER.createArrayNode().add("value"));
    ObjectNode props = MAPPER.createObjectNode();
    props.set("value", MAPPER.createObjectNode().put("type", "string"));
    schema.set("properties", props);
    return schema;
  }
}
