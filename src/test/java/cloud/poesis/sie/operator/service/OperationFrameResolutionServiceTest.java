package cloud.poesis.sie.operator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cloud.poesis.sie.operator.client.DefinitionManagerClient;
import cloud.poesis.sie.operator.dto.ArchetypeAscriptionDto;
import cloud.poesis.sie.operator.dto.EffectorAscriptionDto;
import cloud.poesis.sie.operator.dto.InteractionAscriptionDto;
import cloud.poesis.sie.operator.dto.MechanismAscriptionDto;
import cloud.poesis.sie.operator.dto.OperationFrameDto;
import cloud.poesis.sie.operator.dto.ReceptorAscriptionDto;
import cloud.poesis.sie.operator.exception.OperationFrameResolutionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OperationFrameResolutionServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final UUID OPERATOR_MECHANISM_ID = UUID.randomUUID();
  private static final UUID OPERATOR_RECEPTOR_ID = UUID.randomUUID();

  @Mock private DefinitionManagerClient client;

  private OperationFrameResolutionService resolver;

  @BeforeEach
  void setUp() {
    resolver = new OperationFrameResolutionService(client);
  }

  /** Stubs the on-demand operator identity resolution chain used by validateOperability. */
  private void stubOperatorResolution() {
    ObjectNode mechNode = MAPPER.createObjectNode();
    mechNode.put("id", OPERATOR_MECHANISM_ID.toString());
    when(client.findAscription("MECHANISM", Map.of("function", "run-operation")))
        .thenReturn(Optional.of(mechNode));
    when(client.findReceptors(OPERATOR_MECHANISM_ID))
        .thenReturn(
            List.of(
                new ReceptorAscriptionDto(
                    OPERATOR_RECEPTOR_ID,
                    "ACTIVE",
                    1,
                    "gsmarc://gsm/Receptor/v1",
                    OPERATOR_MECHANISM_ID,
                    archetypeId("OperationRequest"))));
  }

  @Test
  void rejectsUnwiredMechanismWithNoPorts() {
    stubOperatorResolution();
    UUID mechAscId = UUID.randomUUID();
    MechanismAscriptionDto mechanism = mechanismAscription(mechAscId, "test-rule");
    when(client.getMechanismAscription(mechAscId)).thenReturn(mechanism);
    when(client.findReceptors(mechAscId)).thenReturn(Collections.emptyList());
    when(client.findEffectors(mechAscId)).thenReturn(Collections.emptyList());

    assertThatThrownBy(() -> resolver.resolve(mechAscId))
        .isInstanceOf(OperationFrameResolutionException.class)
        .hasMessageContaining("not wired");
  }

  @Test
  void resolvesFrameWithReceptorAndEffector() {
    stubOperatorResolution();
    UUID mechAscId = UUID.randomUUID();
    UUID triggerArchAscId = UUID.randomUUID();
    UUID findingArchAscId = UUID.randomUUID();
    String triggerArchetypeId = archetypeId("AppraisalTrigger");
    String findingArchetypeId = archetypeId("AppraisalFinding");

    MechanismAscriptionDto mechanism =
        mechanismAscription(mechAscId, "evt = sys.receive(\"Trigger\")");
    when(client.getMechanismAscription(mechAscId)).thenReturn(mechanism);

    UUID receptorId = UUID.randomUUID();
    ReceptorAscriptionDto receptor =
        new ReceptorAscriptionDto(
            receptorId, "ACTIVE", 1, "gsmarc://gsm/Receptor/v1", mechAscId, triggerArchetypeId);
    when(client.findReceptors(mechAscId)).thenReturn(List.of(receptor));

    UUID effectorId = UUID.randomUUID();
    EffectorAscriptionDto effector =
        new EffectorAscriptionDto(
            effectorId, "ACTIVE", 1, "gsmarc://gsm/Effector/v1", mechAscId, findingArchetypeId);
    when(client.findEffectors(mechAscId)).thenReturn(List.of(effector));

    // Wire the effector to the operator's receptor
    InteractionAscriptionDto wiring =
        new InteractionAscriptionDto(
            UUID.randomUUID(), "ACTIVE", 1, effectorId, OPERATOR_RECEPTOR_ID);
    when(client.findActiveInteractionsForEffector(effectorId)).thenReturn(List.of(wiring));

    ArchetypeAscriptionDto triggerArchetype =
        archetypeAscription(triggerArchAscId, triggerArchetypeId, "AppraisalTrigger");
    when(client.getArchetypeAscription(triggerArchetypeId)).thenReturn(triggerArchetype);

    ArchetypeAscriptionDto findingArchetype =
        archetypeAscription(findingArchAscId, findingArchetypeId, "AppraisalFinding");
    when(client.getArchetypeAscription(findingArchetypeId)).thenReturn(findingArchetype);

    OperationFrameDto frame = resolver.resolve(mechAscId);

    assertThat(frame.receptors()).hasSize(1);
    assertThat(frame.findArchetype(frame.receptors().getFirst().archetype()))
        .isPresent()
        .get()
        .extracting(ArchetypeAscriptionDto::title)
        .isEqualTo("AppraisalTrigger");
    assertThat(frame.effectors()).hasSize(1);
    assertThat(frame.findArchetype(frame.effectors().getFirst().archetype()))
        .isPresent()
        .get()
        .extracting(ArchetypeAscriptionDto::title)
        .isEqualTo("AppraisalFinding");
    assertThat(frame.archetypes()).containsKeys(triggerArchAscId, findingArchAscId);
  }

  @Test
  void rejectsArchetypeWhoseUriDiffersFromRequestedUri() {
    stubOperatorResolution();
    UUID mechAscId = UUID.randomUUID();
    String requestedId = archetypeId("Requested");
    String returnedId = archetypeId("Returned");
    when(client.getMechanismAscription(mechAscId))
        .thenReturn(mechanismAscription(mechAscId, "rule"));
    when(client.findReceptors(mechAscId))
        .thenReturn(
            List.of(
                new ReceptorAscriptionDto(
                    UUID.randomUUID(),
                    "ACTIVE",
                    1,
                    "gsmarc://gsm/Receptor/v1",
                    mechAscId,
                    requestedId)));

    UUID effectorId = UUID.randomUUID();
    when(client.findEffectors(mechAscId))
        .thenReturn(
            List.of(
                new EffectorAscriptionDto(
                    effectorId, "ACTIVE", 1, "gsmarc://gsm/Effector/v1", mechAscId, requestedId)));
    when(client.findActiveInteractionsForEffector(effectorId))
        .thenReturn(
            List.of(
                new InteractionAscriptionDto(
                    UUID.randomUUID(), "ACTIVE", 1, effectorId, OPERATOR_RECEPTOR_ID)));
    when(client.getArchetypeAscription(requestedId))
        .thenReturn(archetypeAscription(UUID.randomUUID(), returnedId, "Returned"));

    assertThatThrownBy(() -> resolver.resolve(mechAscId))
        .isInstanceOf(OperationFrameResolutionException.class)
        .hasMessageContaining(requestedId)
        .hasMessageContaining(returnedId);
  }

  @Test
  void throwsWhenMechanismNotFound() {
    UUID mechAscId = UUID.randomUUID();
    when(client.getMechanismAscription(mechAscId)).thenReturn(null);

    assertThatThrownBy(() -> resolver.resolve(mechAscId))
        .isInstanceOf(OperationFrameResolutionException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void throwsWhenMechanismIsDraft() {
    UUID mechAscId = UUID.randomUUID();
    MechanismAscriptionDto mechanism =
        new MechanismAscriptionDto(mechAscId, "DRAFT", 1, UUID.randomUUID(), "test", "rule");
    when(client.getMechanismAscription(mechAscId)).thenReturn(mechanism);

    assertThatThrownBy(() -> resolver.resolve(mechAscId))
        .isInstanceOf(OperationFrameResolutionException.class)
        .hasMessageContaining("non-executable status")
        .hasMessageContaining("DRAFT");
  }

  @Test
  void allowsDeprecatedMechanism() {
    stubOperatorResolution();
    UUID mechAscId = UUID.randomUUID();
    UUID archAscId = UUID.randomUUID();
    MechanismAscriptionDto mechanism =
        new MechanismAscriptionDto(mechAscId, "DEPRECATED", 1, UUID.randomUUID(), "test", "rule");
    when(client.getMechanismAscription(mechAscId)).thenReturn(mechanism);
    when(client.findReceptors(mechAscId)).thenReturn(Collections.emptyList());

    UUID effectorId = UUID.randomUUID();
    String archetypeUri = archetypeId("SomeArchetype");
    EffectorAscriptionDto effector =
        new EffectorAscriptionDto(
            effectorId, "ACTIVE", 1, "gsmarc://gsm/Effector/v1", mechAscId, archetypeUri);
    when(client.findEffectors(mechAscId)).thenReturn(List.of(effector));

    InteractionAscriptionDto wiring =
        new InteractionAscriptionDto(
            UUID.randomUUID(), "ACTIVE", 1, effectorId, OPERATOR_RECEPTOR_ID);
    when(client.findActiveInteractionsForEffector(effectorId)).thenReturn(List.of(wiring));

    ArchetypeAscriptionDto archetype =
        archetypeAscription(archAscId, archetypeUri, "SomeArchetype");
    when(client.getArchetypeAscription(archetypeUri)).thenReturn(archetype);

    OperationFrameDto frame = resolver.resolve(mechAscId);

    assertThat(frame.mechanism().id()).isEqualTo(mechAscId);
  }

  @Test
  void rejectsEffectorWithNoInteractionToOperator() {
    stubOperatorResolution();
    UUID mechAscId = UUID.randomUUID();
    MechanismAscriptionDto mechanism = mechanismAscription(mechAscId, "rule");
    when(client.getMechanismAscription(mechAscId)).thenReturn(mechanism);
    when(client.findReceptors(mechAscId)).thenReturn(Collections.emptyList());

    UUID effectorId = UUID.randomUUID();
    EffectorAscriptionDto effector =
        new EffectorAscriptionDto(
            effectorId, "ACTIVE", 1, "gsmarc://gsm/Effector/v1", mechAscId, archetypeId("Unused"));
    when(client.findEffectors(mechAscId)).thenReturn(List.of(effector));

    // Interaction points to a different receptor, not the operator's
    InteractionAscriptionDto unrelatedWiring =
        new InteractionAscriptionDto(UUID.randomUUID(), "ACTIVE", 1, effectorId, UUID.randomUUID());
    when(client.findActiveInteractionsForEffector(effectorId)).thenReturn(List.of(unrelatedWiring));

    assertThatThrownBy(() -> resolver.resolve(mechAscId))
        .isInstanceOf(OperationFrameResolutionException.class)
        .hasMessageContaining("not wired");
  }

  @Test
  void findsReceptorAndEffectorByExactArchetypeId() {
    UUID mechAscId = UUID.randomUUID();
    String triggerArchetypeId = archetypeId("AppraisalTrigger");
    String findingArchetypeId = "gsmarc://other/AppraisalFinding/v1";

    ArchetypeAscriptionDto triggerArchetype =
        archetypeAscription(UUID.randomUUID(), triggerArchetypeId, "SharedTitle");
    ArchetypeAscriptionDto findingArchetype =
        archetypeAscription(UUID.randomUUID(), findingArchetypeId, "SharedTitle");

    ReceptorAscriptionDto receptor =
        new ReceptorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            "gsmarc://tenant/TriggerPort/v1",
            mechAscId,
            triggerArchetypeId);
    EffectorAscriptionDto effector =
        new EffectorAscriptionDto(
            UUID.randomUUID(),
            "ACTIVE",
            1,
            "gsmarc://tenant/FindingPort/v1",
            mechAscId,
            findingArchetypeId);

    MechanismAscriptionDto mechanism = mechanismAscription(mechAscId, "rule");

    OperationFrameDto frame =
        new OperationFrameDto(
            mechanism,
            List.of(receptor),
            List.of(effector),
            Map.of(
                triggerArchetype.id(), triggerArchetype, findingArchetype.id(), findingArchetype));

    assertThat(frame.findReceptorByArchetypeId(triggerArchetypeId)).isPresent();
    assertThat(frame.findReceptorByArchetypeId("SharedTitle")).isEmpty();
    assertThat(frame.findEffectorByArchetypeId(findingArchetypeId)).isPresent();
    assertThat(frame.findEffectorByArchetypeId("SharedTitle")).isEmpty();
    assertThat(frame.findReceptorByPortArchetypeId("gsmarc://tenant/TriggerPort/v1")).isPresent();
    assertThat(frame.findEffectorByPortArchetypeId("gsmarc://tenant/FindingPort/v1")).isPresent();
    assertThat(frame.findSchema(triggerArchetypeId)).isPresent();
    assertThat(frame.findSchema("SharedTitle")).isEmpty();
  }

  // --- Helpers ---

  private MechanismAscriptionDto mechanismAscription(UUID id, String ruleSource) {
    return new MechanismAscriptionDto(id, "ACTIVE", 1, UUID.randomUUID(), "test", ruleSource);
  }

  private static String archetypeId(String title) {
    return "gsmarc://test/" + title + "/v1";
  }

  private ArchetypeAscriptionDto archetypeAscription(UUID id, String archetypeUri, String title) {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("$id", archetypeUri);
    schema.put("title", title);
    schema.put("type", "object");
    return new ArchetypeAscriptionDto(id, "ACTIVE", 1, title, schema);
  }
}
