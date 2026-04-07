package cloud.poesis.sie.operator.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AscriptionDtoValidationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final UUID VALID_UUID = UUID.fromString("01961234-5678-7000-8000-000000000001");
  private static final String VALID_UUID_STR = VALID_UUID.toString();

  // --- MechanismAscriptionDto ---

  @Test
  void mechanismRejectsNullId() {
    assertThatThrownBy(
            () -> new MechanismAscriptionDto(null, "ACTIVE", 1, VALID_UUID, "fn", "rule"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("id is required");
  }

  @Test
  void mechanismRejectsNullStatus() {
    assertThatThrownBy(
            () -> new MechanismAscriptionDto(VALID_UUID, null, 1, VALID_UUID, "fn", "rule"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("status is required");
  }

  @Test
  void mechanismRejectsNullStructure() {
    assertThatThrownBy(
            () -> new MechanismAscriptionDto(VALID_UUID, "ACTIVE", 1, null, "fn", "rule"))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("structure");
  }

  @Test
  void mechanismRejectsBlankFunction() {
    assertThatThrownBy(
            () -> new MechanismAscriptionDto(VALID_UUID, "ACTIVE", 1, VALID_UUID, "  ", "rule"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("function");
  }

  @Test
  void mechanismRejectsBlankRule() {
    assertThatThrownBy(
            () -> new MechanismAscriptionDto(VALID_UUID, "ACTIVE", 1, VALID_UUID, "fn", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("rule");
  }

  @Test
  void mechanismFactoryRejectsNullStatement() {
    assertThatThrownBy(() -> MechanismAscriptionDto.fromJson(VALID_UUID, "ACTIVE", 1, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("statement is required");
  }

  @Test
  void mechanismFactoryRejectsMissingStructure() {
    ObjectNode statement = MAPPER.createObjectNode().put("function", "test").put("rule", "x = 1");
    assertThatThrownBy(() -> MechanismAscriptionDto.fromJson(VALID_UUID, "ACTIVE", 1, statement))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("structure");
  }

  // --- EffectorAscriptionDto ---

  @Test
  void effectorRejectsNullMechanism() {
    assertThatThrownBy(() -> new EffectorAscriptionDto(VALID_UUID, "ACTIVE", 1, null, VALID_UUID))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("mechanism");
  }

  @Test
  void effectorRejectsNullArchetype() {
    assertThatThrownBy(() -> new EffectorAscriptionDto(VALID_UUID, "ACTIVE", 1, VALID_UUID, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("archetype");
  }

  @Test
  void effectorFactoryRejectsMissingMechanism() {
    ObjectNode statement = MAPPER.createObjectNode().put("archetype", VALID_UUID_STR);
    assertThatThrownBy(() -> EffectorAscriptionDto.fromJson(VALID_UUID, "ACTIVE", 1, statement))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mechanism");
  }

  @Test
  void effectorFactoryRejectsMissingArchetype() {
    ObjectNode statement = MAPPER.createObjectNode().put("mechanism", VALID_UUID_STR);
    assertThatThrownBy(() -> EffectorAscriptionDto.fromJson(VALID_UUID, "ACTIVE", 1, statement))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("archetype");
  }

  // --- ReceptorAscriptionDto ---

  @Test
  void receptorRejectsNullMechanism() {
    assertThatThrownBy(() -> new ReceptorAscriptionDto(VALID_UUID, "ACTIVE", 1, null, VALID_UUID))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("mechanism");
  }

  @Test
  void receptorFactoryRejectsMissingMechanism() {
    ObjectNode statement = MAPPER.createObjectNode().put("archetype", VALID_UUID_STR);
    assertThatThrownBy(() -> ReceptorAscriptionDto.fromJson(VALID_UUID, "ACTIVE", 1, statement))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("mechanism");
  }

  @Test
  void receptorFactoryRejectsMissingArchetype() {
    ObjectNode statement = MAPPER.createObjectNode().put("mechanism", VALID_UUID_STR);
    assertThatThrownBy(() -> ReceptorAscriptionDto.fromJson(VALID_UUID, "ACTIVE", 1, statement))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("archetype");
  }

  // --- InteractionAscriptionDto ---

  @Test
  void interactionRejectsNullEffector() {
    assertThatThrownBy(
            () -> new InteractionAscriptionDto(VALID_UUID, "ACTIVE", 1, null, VALID_UUID))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("effector");
  }

  @Test
  void interactionRejectsNullReceptor() {
    assertThatThrownBy(
            () -> new InteractionAscriptionDto(VALID_UUID, "ACTIVE", 1, VALID_UUID, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("receptor");
  }

  @Test
  void interactionFactoryRejectsMissingEffector() {
    ObjectNode statement = MAPPER.createObjectNode().put("receptor", VALID_UUID_STR);
    assertThatThrownBy(() -> InteractionAscriptionDto.fromJson(VALID_UUID, "ACTIVE", 1, statement))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("effector");
  }

  @Test
  void interactionFactoryRejectsMissingReceptor() {
    ObjectNode statement = MAPPER.createObjectNode().put("effector", VALID_UUID_STR);
    assertThatThrownBy(() -> InteractionAscriptionDto.fromJson(VALID_UUID, "ACTIVE", 1, statement))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("receptor");
  }

  // --- StructureAscriptionDto ---

  @Test
  void structureRejectsBlankPurpose() {
    assertThatThrownBy(() -> new StructureAscriptionDto(VALID_UUID, "ACTIVE", 1, ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("purpose");
  }

  @Test
  void structureRejectsNullPurpose() {
    assertThatThrownBy(() -> new StructureAscriptionDto(VALID_UUID, "ACTIVE", 1, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("purpose");
  }

  @Test
  void structureFactoryRejectsMissingPurpose() {
    ObjectNode statement = MAPPER.createObjectNode();
    assertThatThrownBy(() -> StructureAscriptionDto.fromJson(VALID_UUID, "ACTIVE", 1, statement))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("purpose");
  }

  @Test
  void structureFactoryRejectsNullStatement() {
    assertThatThrownBy(() -> StructureAscriptionDto.fromJson(VALID_UUID, "ACTIVE", 1, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("statement");
  }

  @Test
  void structureHappyPath() {
    var dto = new StructureAscriptionDto(VALID_UUID, "ACTIVE", 1, "test purpose");
    assertThat(dto.id()).isEqualTo(VALID_UUID);
    assertThat(dto.status()).isEqualTo("ACTIVE");
    assertThat(dto.version()).isEqualTo(1);
    assertThat(dto.purpose()).isEqualTo("test purpose");
  }

  @Test
  void structureFromJsonHappyPath() {
    ObjectNode statement = MAPPER.createObjectNode().put("purpose", "my purpose");
    var dto = StructureAscriptionDto.fromJson(VALID_UUID, "ACTIVE", 1, statement);
    assertThat(dto.id()).isEqualTo(VALID_UUID);
    assertThat(dto.purpose()).isEqualTo("my purpose");
  }

  // --- ArchetypeAscriptionDto ---

  @Test
  void archetypeRejectsNullId() {
    ObjectNode schema = MAPPER.createObjectNode();
    assertThatThrownBy(() -> new ArchetypeAscriptionDto(null, "ACTIVE", 1, "title", schema))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("id is required");
  }

  @Test
  void archetypeRejectsNullSchema() {
    assertThatThrownBy(() -> new ArchetypeAscriptionDto(VALID_UUID, "ACTIVE", 1, "title", null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("statement is required");
  }

  @Test
  void archetypeFactoryRejectsNullStatement() {
    assertThatThrownBy(() -> ArchetypeAscriptionDto.fromJson(VALID_UUID, "ACTIVE", 1, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("statement is required");
  }
}
