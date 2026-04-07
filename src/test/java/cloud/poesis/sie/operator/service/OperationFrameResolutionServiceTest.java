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
                    OPERATOR_RECEPTOR_ID, "ACTIVE", 1, OPERATOR_MECHANISM_ID, UUID.randomUUID())));
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

    MechanismAscriptionDto mechanism =
        mechanismAscription(mechAscId, "evt = sys.receive(\"Trigger\")");
    when(client.getMechanismAscription(mechAscId)).thenReturn(mechanism);

    UUID receptorId = UUID.randomUUID();
    ReceptorAscriptionDto receptor =
        new ReceptorAscriptionDto(receptorId, "ACTIVE", 1, mechAscId, triggerArchAscId);
    when(client.findReceptors(mechAscId)).thenReturn(List.of(receptor));

    UUID effectorId = UUID.randomUUID();
    EffectorAscriptionDto effector =
        new EffectorAscriptionDto(effectorId, "ACTIVE", 1, mechAscId, findingArchAscId);
    when(client.findEffectors(mechAscId)).thenReturn(List.of(effector));

    // Wire the effector to the operator's receptor
    InteractionAscriptionDto wiring =
        new InteractionAscriptionDto(
            UUID.randomUUID(), "ACTIVE", 1, effectorId, OPERATOR_RECEPTOR_ID);
    when(client.findActiveInteractionsForEffector(effectorId)).thenReturn(List.of(wiring));

    ArchetypeAscriptionDto triggerArchetype =
        archetypeAscription(triggerArchAscId, "AppraisalTrigger");
    when(client.getArchetypeAscription(triggerArchAscId)).thenReturn(triggerArchetype);

    ArchetypeAscriptionDto findingArchetype =
        archetypeAscription(findingArchAscId, "AppraisalFinding");
    when(client.getArchetypeAscription(findingArchAscId)).thenReturn(findingArchetype);

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
    EffectorAscriptionDto effector =
        new EffectorAscriptionDto(effectorId, "ACTIVE", 1, mechAscId, archAscId);
    when(client.findEffectors(mechAscId)).thenReturn(List.of(effector));

    InteractionAscriptionDto wiring =
        new InteractionAscriptionDto(
            UUID.randomUUID(), "ACTIVE", 1, effectorId, OPERATOR_RECEPTOR_ID);
    when(client.findActiveInteractionsForEffector(effectorId)).thenReturn(List.of(wiring));

    ArchetypeAscriptionDto archetype = archetypeAscription(archAscId, "SomeArchetype");
    when(client.getArchetypeAscription(archAscId)).thenReturn(archetype);

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
        new EffectorAscriptionDto(effectorId, "ACTIVE", 1, mechAscId, UUID.randomUUID());
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
  void findsReceptorAndEffectorByArchetypeName() {
    UUID mechAscId = UUID.randomUUID();
    ObjectNode schema = MAPPER.createObjectNode().put("type", "object");

    ArchetypeAscriptionDto triggerArchetype =
        new ArchetypeAscriptionDto(UUID.randomUUID(), "ACTIVE", 1, "AppraisalTrigger", schema);
    ArchetypeAscriptionDto findingArchetype =
        new ArchetypeAscriptionDto(UUID.randomUUID(), "ACTIVE", 1, "AppraisalFinding", schema);

    ReceptorAscriptionDto receptor =
        new ReceptorAscriptionDto(UUID.randomUUID(), "ACTIVE", 1, mechAscId, triggerArchetype.id());
    EffectorAscriptionDto effector =
        new EffectorAscriptionDto(UUID.randomUUID(), "ACTIVE", 1, mechAscId, findingArchetype.id());

    MechanismAscriptionDto mechanism = mechanismAscription(mechAscId, "rule");

    OperationFrameDto frame =
        new OperationFrameDto(
            mechanism,
            List.of(receptor),
            List.of(effector),
            Map.of(
                triggerArchetype.id(), triggerArchetype, findingArchetype.id(), findingArchetype));

    assertThat(frame.findReceptorByArchetypeName("AppraisalTrigger")).isPresent();
    assertThat(frame.findReceptorByArchetypeName("NonExistent")).isEmpty();
    assertThat(frame.findEffectorByArchetypeName("AppraisalFinding")).isPresent();
    assertThat(frame.findEffectorByArchetypeName("NonExistent")).isEmpty();
    assertThat(frame.findSchema("AppraisalTrigger")).isPresent();
    assertThat(frame.findSchema("NonExistent")).isEmpty();
  }

  // --- Helpers ---

  private MechanismAscriptionDto mechanismAscription(UUID id, String ruleSource) {
    return new MechanismAscriptionDto(id, "ACTIVE", 1, UUID.randomUUID(), "test", ruleSource);
  }

  private ArchetypeAscriptionDto archetypeAscription(UUID id, String title) {
    ObjectNode schema = MAPPER.createObjectNode();
    schema.put("title", title);
    schema.put("type", "object");
    return new ArchetypeAscriptionDto(id, "ACTIVE", 1, title, schema);
  }
}
