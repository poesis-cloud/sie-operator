package cloud.poesis.sie.operator.client;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.poesis.sie.operator.dto.ArchetypeAscriptionDto;
import cloud.poesis.sie.operator.dto.EffectorAscriptionDto;
import cloud.poesis.sie.operator.dto.InteractionAscriptionDto;
import cloud.poesis.sie.operator.dto.MechanismAscriptionDto;
import cloud.poesis.sie.operator.dto.ReceptorAscriptionDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class DefinitionManagerClientTest {

  private MockWebServer server;
  private DefinitionManagerClient client;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    String baseUrl = server.url("/").toString();
    // remove trailing slash
    baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    client = new DefinitionManagerClient(WebClient.builder().baseUrl(baseUrl).build());
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void fetchesMechanismAscriptionById() throws InterruptedException {
    UUID id = UUID.fromString("01961234-5678-7000-8000-000000000001");
    UUID structureId = UUID.fromString("01961234-5678-7000-8000-000000000010");
    String response =
        """
        {
          "id": "01961234-5678-7000-8000-000000000001",
          "statement": {"structure": "01961234-5678-7000-8000-000000000010", "function": "test", "rule": "event = sys.receive(\\"X\\")"},
          "version": 1,
          "status": "ACTIVE"
        }
        """;
    server.enqueue(
        new MockResponse().setBody(response).setHeader("Content-Type", "application/json"));

    MechanismAscriptionDto ascription = client.getMechanismAscription(id);

    assertThat(ascription.id()).isEqualTo(id);
    assertThat(ascription.status()).isEqualTo("ACTIVE");
    assertThat(ascription.version()).isEqualTo(1);
    assertThat(ascription.structure()).isEqualTo(structureId);
    assertThat(ascription.function()).isEqualTo("test");
    assertThat(ascription.rule()).isEqualTo("event = sys.receive(\"X\")");
    RecordedRequest request = server.takeRequest();
    assertThat(request.getPath()).isEqualTo("/api/v1/ascriptions/" + id);
  }

  @Test
  void fetchesArchetypeAscriptionById() throws InterruptedException {
    UUID id = UUID.fromString("01961234-5678-7000-8000-000000000099");
    String response =
        """
        {
          "id": "01961234-5678-7000-8000-000000000099",
          "statement": {"title": "AppraisalTrigger", "type": "object", "properties": {}},
          "version": 1,
          "status": "ACTIVE"
        }
        """;
    server.enqueue(
        new MockResponse().setBody(response).setHeader("Content-Type", "application/json"));

    ArchetypeAscriptionDto ascription = client.getArchetypeAscription(id);

    assertThat(ascription.id()).isEqualTo(id);
    assertThat(ascription.title()).isEqualTo("AppraisalTrigger");
    assertThat(ascription.schema().path("type").asText()).isEqualTo("object");
    RecordedRequest request = server.takeRequest();
    assertThat(request.getPath()).isEqualTo("/api/v1/ascriptions/" + id);
  }

  @Test
  void fetchesEffectorsForMechanism() throws InterruptedException {
    UUID mechanismAscId = UUID.fromString("01961234-5678-7000-8000-000000000002");
    UUID archetypeId = UUID.fromString("01961234-5678-7000-8000-000000000099");
    String response =
        """
        {
          "_embedded": {
            "ascriptionDtoList": [
              {
                "id": "01961234-5678-7000-8000-000000000020",
                "statement": {"mechanism": "01961234-5678-7000-8000-000000000002", "archetype": "01961234-5678-7000-8000-000000000099"},
                "version": 1,
                "status": "ACTIVE"
              }
            ]
          },
          "page": {"size": 20, "totalElements": 1, "totalPages": 1, "number": 0}
        }
        """;
    server.enqueue(
        new MockResponse().setBody(response).setHeader("Content-Type", "application/json"));

    List<EffectorAscriptionDto> effectors = client.findEffectors(mechanismAscId);

    assertThat(effectors).hasSize(1);
    assertThat(effectors.getFirst().mechanism()).isEqualTo(mechanismAscId);
    assertThat(effectors.getFirst().archetype()).isEqualTo(archetypeId);
    RecordedRequest request = server.takeRequest();
    assertThat(request.getPath()).contains("type=EFFECTOR");
    assertThat(request.getPath()).doesNotContain("status=");
    assertThat(request.getPath()).contains("statement.mechanism=" + mechanismAscId);
  }

  @Test
  void fetchesReceptorsForMechanism() throws InterruptedException {
    UUID mechanismAscId = UUID.fromString("01961234-5678-7000-8000-000000000002");
    UUID archetypeId = UUID.fromString("01961234-5678-7000-8000-000000000088");
    String response =
        """
        {
          "_embedded": {
            "ascriptionDtoList": [
              {
                "id": "01961234-5678-7000-8000-000000000030",
                "statement": {"mechanism": "01961234-5678-7000-8000-000000000002", "archetype": "01961234-5678-7000-8000-000000000088"},
                "version": 1,
                "status": "ACTIVE"
              }
            ]
          },
          "page": {"size": 20, "totalElements": 1, "totalPages": 1, "number": 0}
        }
        """;
    server.enqueue(
        new MockResponse().setBody(response).setHeader("Content-Type", "application/json"));

    List<ReceptorAscriptionDto> receptors = client.findReceptors(mechanismAscId);

    assertThat(receptors).hasSize(1);
    assertThat(receptors.getFirst().mechanism()).isEqualTo(mechanismAscId);
    assertThat(receptors.getFirst().archetype()).isEqualTo(archetypeId);
    RecordedRequest request = server.takeRequest();
    assertThat(request.getPath()).contains("type=RECEPTOR");
    assertThat(request.getPath()).doesNotContain("status=");
    assertThat(request.getPath()).contains("statement.mechanism=" + mechanismAscId);
  }

  @Test
  void fetchesInteractionsForEffector() throws InterruptedException {
    UUID effectorAscId = UUID.fromString("01961234-5678-7000-8000-000000000020");
    UUID receptorId = UUID.fromString("01961234-5678-7000-8000-000000000030");
    String response =
        """
        {
          "_embedded": {
            "ascriptionDtoList": [
              {
                "id": "01961234-5678-7000-8000-000000000040",
                "statement": {
                  "effector": "01961234-5678-7000-8000-000000000020",
                  "receptor": "01961234-5678-7000-8000-000000000030"
                },
                "version": 1,
                "status": "ACTIVE"
              }
            ]
          },
          "page": {"size": 20, "totalElements": 1, "totalPages": 1, "number": 0}
        }
        """;
    server.enqueue(
        new MockResponse().setBody(response).setHeader("Content-Type", "application/json"));

    List<InteractionAscriptionDto> interactions =
        client.findActiveInteractionsForEffector(effectorAscId);

    assertThat(interactions).hasSize(1);
    assertThat(interactions.getFirst().effector()).isEqualTo(effectorAscId);
    assertThat(interactions.getFirst().receptor()).isEqualTo(receptorId);
    RecordedRequest request = server.takeRequest();
    assertThat(request.getPath()).contains("type=INTERACTION");
    assertThat(request.getPath()).contains("statement.effector=" + effectorAscId);
  }

  @Test
  void returnsEmptyListWhenNoEmbeddedContent() {
    String response =
        """
        {
          "page": {"size": 20, "totalElements": 0, "totalPages": 0, "number": 0}
        }
        """;
    server.enqueue(
        new MockResponse().setBody(response).setHeader("Content-Type", "application/json"));

    List<EffectorAscriptionDto> result = client.findEffectors(UUID.randomUUID());
    assertThat(result).isEmpty();
  }

  @Test
  void findAscriptionReturnsFirstMatchWhenFound() throws InterruptedException {
    UUID ascId = UUID.fromString("01961234-5678-7000-8000-000000000050");
    String response =
        """
        {
          "_embedded": {
            "ascriptionDtoList": [
              {
                "id": "01961234-5678-7000-8000-000000000050",
                "statement": {"title": "StructureArchetype", "type": "object"},
                "version": 1,
                "status": "ACTIVE"
              }
            ]
          },
          "page": {"size": 20, "totalElements": 1, "totalPages": 1, "number": 0}
        }
        """;
    server.enqueue(
        new MockResponse().setBody(response).setHeader("Content-Type", "application/json"));

    Optional<JsonNode> result =
        client.findAscription("ARCHETYPE", Map.of("title", "StructureArchetype"));

    assertThat(result).isPresent();
    assertThat(result.get().path("id").asText()).isEqualTo(ascId.toString());
    RecordedRequest request = server.takeRequest();
    assertThat(request.getPath()).contains("type=ARCHETYPE");
    assertThat(request.getPath()).contains("statement.title=StructureArchetype");
  }

  @Test
  void findAscriptionReturnsEmptyWhenNotFound() {
    String response =
        """
        {
          "page": {"size": 20, "totalElements": 0, "totalPages": 0, "number": 0}
        }
        """;
    server.enqueue(
        new MockResponse().setBody(response).setHeader("Content-Type", "application/json"));

    Optional<JsonNode> result =
        client.findAscription("STRUCTURE", Map.of("purpose", "nonexistent"));

    assertThat(result).isEmpty();
  }

  @Test
  void createAscriptionPostsAndReturnsCreated() throws Exception {
    UUID archetypeId = UUID.fromString("01961234-5678-7000-8000-000000000060");
    UUID createdId = UUID.fromString("01961234-5678-7000-8000-000000000061");
    String response =
        """
        {
          "id": "01961234-5678-7000-8000-000000000061",
          "statement": {"purpose": "sie-operator"},
          "version": 1,
          "status": "ACTIVE"
        }
        """;
    server.enqueue(
        new MockResponse()
            .setResponseCode(201)
            .setBody(response)
            .setHeader("Content-Type", "application/json"));

    ObjectMapper mapper = new ObjectMapper();
    JsonNode statement = mapper.createObjectNode().put("purpose", "sie-operator");

    JsonNode result = client.createAscription(archetypeId, statement);

    assertThat(result.path("id").asText()).isEqualTo(createdId.toString());
    assertThat(result.path("statement").path("purpose").asText()).isEqualTo("sie-operator");
    RecordedRequest request = server.takeRequest();
    assertThat(request.getMethod()).isEqualTo("POST");
    assertThat(request.getPath()).isEqualTo("/api/v1/ascriptions");
    JsonNode body = mapper.readTree(request.getBody().readUtf8());
    assertThat(body.path("archetypeId").asText()).isEqualTo(archetypeId.toString());
    assertThat(body.path("statement").path("purpose").asText()).isEqualTo("sie-operator");
  }
}
