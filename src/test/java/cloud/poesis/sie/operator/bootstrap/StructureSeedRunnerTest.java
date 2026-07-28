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

  // Base archetype IDs
  private static final UUID BASE_ARCHETYPE_ID = UUID.randomUUID();
  private static final UUID STRUCTURE_ARCHETYPE_ID = UUID.randomUUID();
  private static final UUID MECHANISM_ARCHETYPE_ID = UUID.randomUUID();
  private static final UUID RECEPTOR_ARCHETYPE_ID = UUID.randomUUID();
  private static final UUID EFFECTOR_ARCHETYPE_ID = UUID.randomUUID();

  // Created ascription IDs
  private static final UUID OP_REQUEST_ARCH_ID = UUID.randomUUID();
  private static final UUID OP_RESPONSE_ARCH_ID = UUID.randomUUID();
  private static final UUID STRUCTURE_ASC_ID = UUID.randomUUID();
  private static final UUID MECHANISM_ASC_ID = UUID.randomUUID();
  private static final UUID RECEPTOR_ASC_ID = UUID.randomUUID();
  private static final UUID EFFECTOR_ASC_ID = UUID.randomUUID();

  private static final List<String> PROTOCOL_ARCHETYPE_TITLES =
      List.of(
          "FrameworkAscription",
          "FrameworkStructure",
          "FrameworkMechanism",
          "FrameworkEffector",
          "FrameworkReceptor",
          "FrameworkInteraction",
          "FrameworkDirective",
          "FrameworkNorm",
          "HttpRequest",
          "HttpResponse",
          "HttpRequestEffector",
          "HttpResponseEffector",
          "HttpRequestReceptor",
          "HttpResponseReceptor",
          "HttpRequestInteraction",
          "HttpResponseInteraction",
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
    stubBaseArchetype("Archetype", BASE_ARCHETYPE_ID);
    stubBaseArchetype("Structure", STRUCTURE_ARCHETYPE_ID);
    stubBaseArchetype("Mechanism", MECHANISM_ARCHETYPE_ID);
    stubBaseArchetype("Receptor", RECEPTOR_ARCHETYPE_ID);
    stubBaseArchetype("Effector", EFFECTOR_ARCHETYPE_ID);

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

    // Creations return IDs (2 custom + 8 framework base + 12 protocol archetypes)
    when(client.createAscription(eq(BASE_ARCHETYPE_ID), any()))
        .thenAnswer(invocation -> ascriptionNode(UUID.randomUUID()));
    when(client.createAscription(eq(STRUCTURE_ARCHETYPE_ID), any()))
        .thenReturn(ascriptionNode(STRUCTURE_ASC_ID));
    when(client.createAscription(eq(MECHANISM_ARCHETYPE_ID), any()))
        .thenReturn(ascriptionNode(MECHANISM_ASC_ID));
    when(client.createAscription(eq(RECEPTOR_ARCHETYPE_ID), any()))
        .thenReturn(ascriptionNode(RECEPTOR_ASC_ID));
    when(client.createAscription(eq(EFFECTOR_ARCHETYPE_ID), any()))
        .thenReturn(ascriptionNode(EFFECTOR_ASC_ID));

    service.run(null);

    // 2 custom + 12 protocol = 14 ARCHETYPE ascriptions created
    verify(client, times(22)).createAscription(eq(BASE_ARCHETYPE_ID), any());
    verify(client).createAscription(eq(STRUCTURE_ARCHETYPE_ID), any());
    verify(client).createAscription(eq(MECHANISM_ARCHETYPE_ID), any());
    verify(client).createAscription(eq(RECEPTOR_ARCHETYPE_ID), any());
    verify(client).createAscription(eq(EFFECTOR_ARCHETYPE_ID), any());
  }

  @Test
  void skipsCreationWhenAscriptionsAlreadyExist() throws Exception {
    // Resolve base archetypes
    stubBaseArchetype("Archetype", BASE_ARCHETYPE_ID);
    stubBaseArchetype("Structure", STRUCTURE_ARCHETYPE_ID);
    stubBaseArchetype("Mechanism", MECHANISM_ARCHETYPE_ID);
    stubBaseArchetype("Receptor", RECEPTOR_ARCHETYPE_ID);
    stubBaseArchetype("Effector", EFFECTOR_ARCHETYPE_ID);

    // Everything already exists
    when(client.findAscription(eq("ARCHETYPE"), eq(Map.of("title", "OperationRequest"))))
        .thenReturn(Optional.of(ascriptionNode(OP_REQUEST_ARCH_ID)));
    when(client.findAscription(eq("ARCHETYPE"), eq(Map.of("title", "OperationResponse"))))
        .thenReturn(Optional.of(ascriptionNode(OP_RESPONSE_ARCH_ID)));
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
    stubBaseArchetype("Archetype", BASE_ARCHETYPE_ID);
    stubBaseArchetype("Structure", STRUCTURE_ARCHETYPE_ID);
    stubBaseArchetype("Mechanism", MECHANISM_ARCHETYPE_ID);
    stubBaseArchetype("Receptor", RECEPTOR_ARCHETYPE_ID);
    stubBaseArchetype("Effector", EFFECTOR_ARCHETYPE_ID);

    when(client.findAscription(eq("ARCHETYPE"), eq(Map.of("title", "OperationRequest"))))
        .thenReturn(Optional.of(ascriptionNode(OP_REQUEST_ARCH_ID)));
    when(client.findAscription(eq("ARCHETYPE"), eq(Map.of("title", "OperationResponse"))))
        .thenReturn(Optional.of(ascriptionNode(OP_RESPONSE_ARCH_ID)));
    stubProtocolArchetypesExist();
    when(client.findAscription(eq("STRUCTURE"), eq(Map.of("purpose", "sie-operator"))))
        .thenReturn(Optional.of(ascriptionNode(STRUCTURE_ASC_ID)));
    when(client.findAscription(eq("MECHANISM"), eq(Map.of("function", "run-operation"))))
        .thenReturn(Optional.empty());
    when(client.findAscription(eq("RECEPTOR"), any()))
        .thenReturn(Optional.of(ascriptionNode(RECEPTOR_ASC_ID)));
    when(client.findAscription(eq("EFFECTOR"), any()))
        .thenReturn(Optional.of(ascriptionNode(EFFECTOR_ASC_ID)));

    when(client.createAscription(eq(MECHANISM_ARCHETYPE_ID), any()))
        .thenReturn(ascriptionNode(MECHANISM_ASC_ID));

    service.run(null);

    // Verify the mechanism creation was called with the resolved structure ID
    ArgumentCaptor<com.fasterxml.jackson.databind.JsonNode> stmtCaptor =
        ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
    verify(client).createAscription(eq(MECHANISM_ARCHETYPE_ID), stmtCaptor.capture());

    com.fasterxml.jackson.databind.JsonNode capturedStmt = stmtCaptor.getValue();
    assertThat(capturedStmt.path("structure").asText()).isEqualTo(STRUCTURE_ASC_ID.toString());
    assertThat(capturedStmt.path("function").asText()).isEqualTo("run-operation");
    assertThat(capturedStmt.path("rule").asText()).isNotBlank();
  }

  // --- Helpers ---

  private void stubBaseArchetype(String title, UUID id) {
    when(client.findAscription("ARCHETYPE", Map.of("title", title)))
        .thenReturn(Optional.of(ascriptionNode(id)));
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
}
