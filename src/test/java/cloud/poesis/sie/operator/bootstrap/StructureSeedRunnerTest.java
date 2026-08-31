package cloud.poesis.sie.operator.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cloud.poesis.sie.operator.client.DefinitionManagerClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StructureSeedRunnerTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  // Base archetype URIs (typing references are Archetype URIs, not UUIDs)
  private static final String BASE_ARCHETYPE_URI = "gsmarc://gsm/Archetype/v1";
  private static final String STRUCTURE_ARCHETYPE_URI = "gsmarc://gsm/Structure/v1";
  private static final String MECHANISM_ARCHETYPE_URI = "gsmarc://gsm/Mechanism/v1";
  private static final String RECEPTOR_ARCHETYPE_URI = "gsmarc://gsm/Receptor/v1";
  private static final String EFFECTOR_ARCHETYPE_URI = "gsmarc://gsm/Effector/v1";

  private static final String OP_REQUEST_ARCH_URI = "gsmarc://sie/OperationRequest/v1";
  private static final String OP_RESPONSE_ARCH_URI = "gsmarc://sie/OperationResponse/v1";

  // Created ascription IDs
  private static final UUID OP_REQUEST_ARCH_ID = UUID.randomUUID();
  private static final UUID OP_RESPONSE_ARCH_ID = UUID.randomUUID();
  private static final UUID STRUCTURE_ASC_ID = UUID.randomUUID();
  private static final UUID MECHANISM_ASC_ID = UUID.randomUUID();
  private static final UUID RECEPTOR_ASC_ID = UUID.randomUUID();
  private static final UUID EFFECTOR_ASC_ID = UUID.randomUUID();

  private static final List<String> PROTOCOL_ARCHETYPE_TITLES =
      List.of(
          "Request",
          "Response",
          "RequestEffector",
          "ResponseEffector",
          "RequestReceptor",
          "ResponseReceptor",
          "RequestInteraction",
          "ResponseInteraction",
          "RelaySignal",
          "RelayEffector",
          "RelayReceptor",
          "RelayInteraction");

  @Mock private DefinitionManagerClient client;

  private StructureSeedRunner service;

  @BeforeEach
  void setUp() {
    service = new StructureSeedRunner(client);
  }

  @Test
  void registersAllAscriptionsOnCleanStartup() throws Exception {
    // Resolve base archetypes
    stubBaseArchetype("Archetype", BASE_ARCHETYPE_URI);
    stubBaseArchetype("Structure", STRUCTURE_ARCHETYPE_URI);
    stubBaseArchetype("Mechanism", MECHANISM_ARCHETYPE_URI);
    stubBaseArchetype("Receptor", RECEPTOR_ARCHETYPE_URI);
    stubBaseArchetype("Effector", EFFECTOR_ARCHETYPE_URI);

    // Nothing exists yet
    when(client.findAscription(eq("ARCHETYPE"), eq(Map.of("title", "OperationRequest"))))
        .thenReturn(Optional.empty());
    when(client.findAscription(eq("ARCHETYPE"), eq(Map.of("title", "OperationResponse"))))
        .thenReturn(Optional.empty());
    stubProtocolArchetypesNotExist();
    when(client.findAscription(eq("STRUCTURE"), eq(Map.of("purpose", "sie-operator"))))
        .thenReturn(Optional.empty());
    when(client.findAscription(eq("MECHANISM"), eq(Map.of("function", "run-operation"))))
        .thenReturn(Optional.empty());
    when(client.findAscription(eq("RECEPTOR"), any())).thenReturn(Optional.empty());
    when(client.findAscription(eq("EFFECTOR"), any())).thenReturn(Optional.empty());

    // Creations return IDs (2 custom + 12 protocol archetypes)
    when(client.createAscription(eq(BASE_ARCHETYPE_URI), any()))
        .thenAnswer(invocation -> archetypeNode(UUID.randomUUID(), "gsmarc://sie/Created/v1"));
    when(client.createAscription(eq(STRUCTURE_ARCHETYPE_URI), any()))
        .thenReturn(ascriptionNode(STRUCTURE_ASC_ID));
    when(client.createAscription(eq(MECHANISM_ARCHETYPE_URI), any()))
        .thenReturn(ascriptionNode(MECHANISM_ASC_ID));
    when(client.createAscription(eq(RECEPTOR_ARCHETYPE_URI), any()))
        .thenReturn(ascriptionNode(RECEPTOR_ASC_ID));
    when(client.createAscription(eq(EFFECTOR_ARCHETYPE_URI), any()))
        .thenReturn(ascriptionNode(EFFECTOR_ASC_ID));

    service.run(null);

    // 2 custom + 12 protocol = 14 ARCHETYPE ascriptions created
    verify(client, times(14)).createAscription(eq(BASE_ARCHETYPE_URI), any());
    verify(client).createAscription(eq(STRUCTURE_ARCHETYPE_URI), any());
    verify(client).createAscription(eq(MECHANISM_ARCHETYPE_URI), any());
    verify(client).createAscription(eq(RECEPTOR_ARCHETYPE_URI), any());
    verify(client).createAscription(eq(EFFECTOR_ARCHETYPE_URI), any());
  }

  @Test
  void skipsCreationWhenAscriptionsAlreadyExist() throws Exception {
    // Resolve base archetypes
    stubBaseArchetype("Archetype", BASE_ARCHETYPE_URI);
    stubBaseArchetype("Structure", STRUCTURE_ARCHETYPE_URI);
    stubBaseArchetype("Mechanism", MECHANISM_ARCHETYPE_URI);
    stubBaseArchetype("Receptor", RECEPTOR_ARCHETYPE_URI);
    stubBaseArchetype("Effector", EFFECTOR_ARCHETYPE_URI);

    // Everything already exists
    when(client.findAscription(eq("ARCHETYPE"), eq(Map.of("title", "OperationRequest"))))
        .thenReturn(Optional.of(archetypeNode(OP_REQUEST_ARCH_ID, OP_REQUEST_ARCH_URI)));
    when(client.findAscription(eq("ARCHETYPE"), eq(Map.of("title", "OperationResponse"))))
        .thenReturn(Optional.of(archetypeNode(OP_RESPONSE_ARCH_ID, OP_RESPONSE_ARCH_URI)));
    stubProtocolArchetypesExist();
    when(client.findAscription(eq("STRUCTURE"), eq(Map.of("purpose", "sie-operator"))))
        .thenReturn(Optional.of(ascriptionNode(STRUCTURE_ASC_ID)));
    when(client.findAscription(eq("MECHANISM"), eq(Map.of("function", "run-operation"))))
        .thenReturn(Optional.of(ascriptionNode(MECHANISM_ASC_ID)));
    when(client.findAscription(eq("RECEPTOR"), any()))
        .thenReturn(Optional.of(ascriptionNode(RECEPTOR_ASC_ID)));
    when(client.findAscription(eq("EFFECTOR"), any()))
        .thenReturn(Optional.of(ascriptionNode(EFFECTOR_ASC_ID)));

    service.run(null);

    // No creations should occur
    verify(client, never()).createAscription(any(), any());
  }

  @Test
  void throwsWhenBaseArchetypeNotFound() {
    // First base archetype lookup returns empty
    when(client.findAscription("ARCHETYPE", Map.of("title", "Archetype")))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Base archetype 'Archetype' not found");
  }

  @Test
  void mechanismStatementResolvesStructureReference() throws Exception {
    stubBaseArchetype("Archetype", BASE_ARCHETYPE_URI);
    stubBaseArchetype("Structure", STRUCTURE_ARCHETYPE_URI);
    stubBaseArchetype("Mechanism", MECHANISM_ARCHETYPE_URI);
    stubBaseArchetype("Receptor", RECEPTOR_ARCHETYPE_URI);
    stubBaseArchetype("Effector", EFFECTOR_ARCHETYPE_URI);

    when(client.findAscription(eq("ARCHETYPE"), eq(Map.of("title", "OperationRequest"))))
        .thenReturn(Optional.of(archetypeNode(OP_REQUEST_ARCH_ID, OP_REQUEST_ARCH_URI)));
    when(client.findAscription(eq("ARCHETYPE"), eq(Map.of("title", "OperationResponse"))))
        .thenReturn(Optional.of(archetypeNode(OP_RESPONSE_ARCH_ID, OP_RESPONSE_ARCH_URI)));
    stubProtocolArchetypesExist();
    when(client.findAscription(eq("STRUCTURE"), eq(Map.of("purpose", "sie-operator"))))
        .thenReturn(Optional.of(ascriptionNode(STRUCTURE_ASC_ID)));
    when(client.findAscription(eq("MECHANISM"), eq(Map.of("function", "run-operation"))))
        .thenReturn(Optional.empty());
    when(client.findAscription(eq("RECEPTOR"), any()))
        .thenReturn(Optional.of(ascriptionNode(RECEPTOR_ASC_ID)));
    when(client.findAscription(eq("EFFECTOR"), any()))
        .thenReturn(Optional.of(ascriptionNode(EFFECTOR_ASC_ID)));

    when(client.createAscription(eq(MECHANISM_ARCHETYPE_URI), any()))
        .thenReturn(ascriptionNode(MECHANISM_ASC_ID));

    service.run(null);

    // Verify the mechanism creation was called with the resolved structure ID
    ArgumentCaptor<com.fasterxml.jackson.databind.JsonNode> stmtCaptor =
        ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
    verify(client).createAscription(eq(MECHANISM_ARCHETYPE_URI), stmtCaptor.capture());

    com.fasterxml.jackson.databind.JsonNode capturedStmt = stmtCaptor.getValue();
    assertThat(capturedStmt.path("structure").asText()).isEqualTo(STRUCTURE_ASC_ID.toString());
    assertThat(capturedStmt.path("function").asText()).isEqualTo("run-operation");
    assertThat(capturedStmt.path("rule").asText()).isNotBlank();
  }

  // --- Helpers ---

  private void stubBaseArchetype(String title, String uri) {
    when(client.findAscription("ARCHETYPE", Map.of("title", title)))
        .thenReturn(Optional.of(archetypeNode(UUID.randomUUID(), uri)));
  }

  private void stubProtocolArchetypesNotExist() {
    for (String title : PROTOCOL_ARCHETYPE_TITLES) {
      when(client.findAscription(eq("ARCHETYPE"), eq(Map.of("title", title))))
          .thenReturn(Optional.empty());
    }
  }

  private void stubProtocolArchetypesExist() {
    for (String title : PROTOCOL_ARCHETYPE_TITLES) {
      when(client.findAscription(eq("ARCHETYPE"), eq(Map.of("title", title))))
          .thenReturn(Optional.of(ascriptionNode(UUID.randomUUID())));
    }
  }

  private static ObjectNode ascriptionNode(UUID id) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("id", id.toString());
    node.put("version", 1);
    node.put("status", "ACTIVE");
    return node;
  }

  /** An Archetype Ascription carries its own URI in {@code statement.$id}. */
  private static ObjectNode archetypeNode(UUID id, String uri) {
    ObjectNode node = ascriptionNode(id);
    node.putObject("statement").put("$id", uri);
    return node;
  }
}
