package cloud.poesis.sie.operator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cloud.poesis.sie.operator.client.DefinitionManagerClient;
import cloud.poesis.sie.operator.dto.ArchetypeAscriptionDto;
import cloud.poesis.sie.operator.dto.EffectorAscriptionDto;
import cloud.poesis.sie.operator.dto.MechanismAscriptionDto;
import cloud.poesis.sie.operator.dto.OperationTopologyDto;
import cloud.poesis.sie.operator.dto.ReceptorAscriptionDto;
import cloud.poesis.sie.operator.exception.TopologyResolutionException;
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
class TopologyResolverServiceTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Mock private DefinitionManagerClient client;

  private TopologyResolverService resolver;

  @BeforeEach
  void setUp() {
    resolver = new TopologyResolverService(client);
  }

  @Test
  void resolvesMinimalTopologyWithNoPorts() {
    UUID mechAscId = UUID.randomUUID();
    MechanismAscriptionDto mechanism = mechanismAscription(mechAscId, "test-rule");
    when(client.getMechanismAscription(mechAscId)).thenReturn(mechanism);
    when(client.findReceptors(mechAscId)).thenReturn(Collections.emptyList());
    when(client.findEffectors(mechAscId)).thenReturn(Collections.emptyList());

    OperationTopologyDto topology = resolver.resolve(mechAscId);

    assertThat(topology.mechanismAscriptionId()).isEqualTo(mechAscId);
    assertThat(topology.getRuleSource()).isEqualTo("test-rule");
    assertThat(topology.receptors()).isEmpty();
    assertThat(topology.effectors()).isEmpty();
    assertThat(topology.archetypes()).isEmpty();
  }

  @Test
  void resolvesTopologyWithReceptorAndEffector() {
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

    ArchetypeAscriptionDto triggerArchetype =
        archetypeAscription(triggerArchAscId, "AppraisalTrigger");
    when(client.getArchetypeAscription(triggerArchAscId)).thenReturn(triggerArchetype);

    ArchetypeAscriptionDto findingArchetype =
        archetypeAscription(findingArchAscId, "AppraisalFinding");
    when(client.getArchetypeAscription(findingArchAscId)).thenReturn(findingArchetype);

    OperationTopologyDto topology = resolver.resolve(mechAscId);

    assertThat(topology.receptors()).hasSize(1);
    assertThat(topology.receptors().getFirst().archetypeName()).isEqualTo("AppraisalTrigger");
    assertThat(topology.effectors()).hasSize(1);
    assertThat(topology.effectors().getFirst().archetypeName()).isEqualTo("AppraisalFinding");
    assertThat(topology.archetypes()).containsKeys("AppraisalTrigger", "AppraisalFinding");
  }

  @Test
  void throwsWhenMechanismNotFound() {
    UUID mechAscId = UUID.randomUUID();
    when(client.getMechanismAscription(mechAscId)).thenReturn(null);

    assertThatThrownBy(() -> resolver.resolve(mechAscId))
        .isInstanceOf(TopologyResolutionException.class)
        .hasMessageContaining("not found");
  }

  @Test
  void throwsWhenMechanismIsDraft() {
    UUID mechAscId = UUID.randomUUID();
    MechanismAscriptionDto mechanism =
        new MechanismAscriptionDto(mechAscId, "DRAFT", 1, UUID.randomUUID(), "test", "rule");
    when(client.getMechanismAscription(mechAscId)).thenReturn(mechanism);

    assertThatThrownBy(() -> resolver.resolve(mechAscId))
        .isInstanceOf(TopologyResolutionException.class)
        .hasMessageContaining("non-executable status")
        .hasMessageContaining("DRAFT");
  }

  @Test
  void allowsDeprecatedMechanism() {
    UUID mechAscId = UUID.randomUUID();
    MechanismAscriptionDto mechanism =
        new MechanismAscriptionDto(mechAscId, "DEPRECATED", 1, UUID.randomUUID(), "test", "rule");
    when(client.getMechanismAscription(mechAscId)).thenReturn(mechanism);
    when(client.findReceptors(mechAscId)).thenReturn(Collections.emptyList());
    when(client.findEffectors(mechAscId)).thenReturn(Collections.emptyList());

    OperationTopologyDto topology = resolver.resolve(mechAscId);

    assertThat(topology.mechanismAscriptionId()).isEqualTo(mechAscId);
  }

  @Test
  void findsReceptorAndEffectorByArchetypeName() {
    UUID mechAscId = UUID.randomUUID();
    ObjectNode schema = MAPPER.createObjectNode().put("type", "object");

    ArchetypeAscriptionDto triggerArchetype =
        new ArchetypeAscriptionDto(UUID.randomUUID(), "ACTIVE", 1, "AppraisalTrigger", schema);
    ArchetypeAscriptionDto findingArchetype =
        new ArchetypeAscriptionDto(UUID.randomUUID(), "ACTIVE", 1, "AppraisalFinding", schema);

    OperationTopologyDto.ResolvedPort receptor =
        new OperationTopologyDto.ResolvedPort(
            UUID.randomUUID(), triggerArchetype.id(), "AppraisalTrigger", schema);
    OperationTopologyDto.ResolvedPort effector =
        new OperationTopologyDto.ResolvedPort(
            UUID.randomUUID(), findingArchetype.id(), "AppraisalFinding", schema);

    MechanismAscriptionDto mechanism = mechanismAscription(mechAscId, "rule");

    OperationTopologyDto topology =
        new OperationTopologyDto(
            mechAscId,
            mechanism,
            List.of(receptor),
            List.of(effector),
            Map.of("AppraisalTrigger", triggerArchetype, "AppraisalFinding", findingArchetype));

    assertThat(topology.findReceptorByArchetypeName("AppraisalTrigger")).isPresent();
    assertThat(topology.findReceptorByArchetypeName("NonExistent")).isEmpty();
    assertThat(topology.findEffectorByArchetypeName("AppraisalFinding")).isPresent();
    assertThat(topology.findEffectorByArchetypeName("NonExistent")).isEmpty();
    assertThat(topology.findSchema("AppraisalTrigger")).isPresent();
    assertThat(topology.findSchema("NonExistent")).isEmpty();
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
