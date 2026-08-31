package cloud.poesis.sie.operator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cloud.poesis.sie.operator.dto.ArchetypeBindingDto;
import cloud.poesis.sie.operator.dto.NormAscriptionDto;
import cloud.poesis.sie.operator.exception.NormApplicabilityEvaluationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NormApplicabilityEvaluationServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String DEPLOYMENT_ID = "gsmarc://tenant/DeploymentProperties/v1";
  private static final String DEPLOYMENT_V2_ID = "gsmarc://tenant/DeploymentProperties/v2";

  private NormApplicabilityEvaluationService service;

  @BeforeEach
  void setUp() {
    service = new NormApplicabilityEvaluationService(new OperationInputValidationService());
  }

  @Test
  void exactVersionBinding_returnsEvaluatedBoolean() {
    NormAscriptionDto norm =
        new NormAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            "ref(\"" + DEPLOYMENT_ID + "\").region == \"us-east-1\"");
    ArchetypeBindingDto binding = binding(DEPLOYMENT_ID, "us-east-1");

    assertThat(service.evaluate(norm, List.of(binding))).isTrue();
  }

  @Test
  void missingBinding_failsBeforeEvaluation() {
    NormAscriptionDto norm =
        new NormAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            "ref(\"" + DEPLOYMENT_ID + "\").region == \"us-east-1\"");

    assertThatThrownBy(() -> service.evaluate(norm, List.of()))
        .isInstanceOf(NormApplicabilityEvaluationException.class)
        .hasMessageContaining(DEPLOYMENT_ID);
  }

  @Test
  void duplicateBinding_failsBeforeSchemaResolution() {
    NormAscriptionDto norm = norm(DEPLOYMENT_ID);
    ArchetypeBindingDto binding = binding(DEPLOYMENT_ID, "us-east-1");

    assertThatThrownBy(() -> service.evaluate(norm, List.of(binding, binding)))
        .isInstanceOf(NormApplicabilityEvaluationException.class)
        .hasMessageContaining("Duplicate")
        .hasMessageContaining(DEPLOYMENT_ID);
  }

  @Test
  void unexpectedExtraBinding_failsBeforeSchemaResolution() {
    NormAscriptionDto norm = norm(DEPLOYMENT_ID);

    assertThatThrownBy(
            () ->
                service.evaluate(
                    norm,
                    List.of(
                        binding(DEPLOYMENT_ID, "us-east-1"),
                        binding(DEPLOYMENT_V2_ID, "us-east-1"))))
        .isInstanceOf(NormApplicabilityEvaluationException.class)
        .hasMessageContaining("target mismatch")
        .hasMessageContaining(DEPLOYMENT_V2_ID);
  }

  @Test
  void computedRefArgument_failsBeforeSchemaResolution() {
    NormAscriptionDto norm =
        new NormAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            "ref(\"" + DEPLOYMENT_ID + "\" + \"\").region == \"us-east-1\"");

    assertThatThrownBy(() -> service.evaluate(norm, List.of(binding(DEPLOYMENT_ID, "us-east-1"))))
        .isInstanceOf(NormApplicabilityEvaluationException.class)
        .hasMessageContaining("string-literal");
  }

  @Test
  void titleBinding_doesNotSatisfyUriTarget() {
    NormAscriptionDto norm = norm(DEPLOYMENT_ID);

    assertThatThrownBy(
            () -> service.evaluate(norm, List.of(binding("DeploymentProperties", "us-east-1"))))
        .isInstanceOf(NormApplicabilityEvaluationException.class)
        .hasMessageContaining("target mismatch")
        .hasMessageContaining("DeploymentProperties");
  }

  @Test
  void stemBinding_doesNotSatisfyUriTarget() {
    NormAscriptionDto norm = norm(DEPLOYMENT_ID);
    String stem = "gsmarc://tenant/DeploymentProperties";

    assertThatThrownBy(() -> service.evaluate(norm, List.of(binding(stem, "us-east-1"))))
        .isInstanceOf(NormApplicabilityEvaluationException.class)
        .hasMessageContaining("target mismatch")
        .hasMessageContaining(stem);
  }

  @Test
  void schemaInvalidBinding_failsBeforeCelEvaluation() {
    NormAscriptionDto norm = norm(DEPLOYMENT_ID);

    assertThatThrownBy(
            () ->
                service.evaluate(
                    norm,
                    List.of(
                        new ArchetypeBindingDto(
                            DEPLOYMENT_ID,
                            MAPPER.createObjectNode().put("region", 42),
                            schema(DEPLOYMENT_ID)))))
        .isInstanceOf(NormApplicabilityEvaluationException.class)
        .hasMessageContaining("Invalid")
        .hasMessageContaining(DEPLOYMENT_ID);
  }

  @Test
  void exactVersionLookup_doesNotFallThroughToAnotherVersion() {
    NormAscriptionDto norm = norm(DEPLOYMENT_ID);

    assertThat(service.evaluate(norm, List.of(binding(DEPLOYMENT_ID, "us-east-1")))).isTrue();
  }

  @Test
  void mismatchedResolvedSchemaIdentityFailsClosed() {
    NormAscriptionDto norm = norm(DEPLOYMENT_ID);
    ArchetypeBindingDto mismatchedBinding =
        new ArchetypeBindingDto(
            DEPLOYMENT_ID,
            MAPPER.createObjectNode().put("region", "us-east-1"),
            schema(DEPLOYMENT_V2_ID));

    assertThatThrownBy(() -> service.evaluate(norm, List.of(mismatchedBinding)))
        .isInstanceOf(NormApplicabilityEvaluationException.class)
        .hasMessageContaining(DEPLOYMENT_ID)
        .hasMessageContaining(DEPLOYMENT_V2_ID);
  }

  @Test
  void invocationBindings_areIsolated() {
    NormAscriptionDto norm = norm(DEPLOYMENT_ID);

    assertThat(service.evaluate(norm, List.of(binding(DEPLOYMENT_ID, "us-east-1")))).isTrue();
    assertThat(service.evaluate(norm, List.of(binding(DEPLOYMENT_ID, "eu-west-1")))).isFalse();
  }

  private static NormAscriptionDto norm(String expressionId) {
    return new NormAscriptionDto(
        UUID.randomUUID(), "ACTIVE", 1, "ref(\"" + expressionId + "\").region == \"us-east-1\"");
  }

  private static ArchetypeBindingDto binding(String archetypeId, String region) {
    return new ArchetypeBindingDto(
        archetypeId, MAPPER.createObjectNode().put("region", region), schema(archetypeId));
  }

  private static com.fasterxml.jackson.databind.JsonNode schema(String archetypeId) {
    return MAPPER
        .createObjectNode()
        .put("$id", archetypeId)
        .put("type", "object")
        .set(
            "properties",
            MAPPER
                .createObjectNode()
                .set("region", MAPPER.createObjectNode().put("type", "string")));
  }
}
