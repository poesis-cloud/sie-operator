package cloud.poesis.sie.operator.bootstrap;

import cloud.poesis.sie.operator.client.DefinitionManagerClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Registers the SIE Operator's own Structure, Mechanism, Receptor, and Effector ascriptions on the
 * Definition Manager at application startup. Idempotent: checks for existing ascriptions before
 * creating new ones.
 */
@Component
@ConditionalOnProperty(name = "op.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
public class StructureSeedRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(StructureSeedRunner.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String STATEMENT_DIR =
      "statement/"; // mapped from def/statement/ via pom.xml

  private static final List<ProtocolArchetype> PROTOCOL_ARCHETYPES =
      List.of(
          new ProtocolArchetype("Request", "protocol/http/Request.archetype.json"),
          new ProtocolArchetype("Response", "protocol/http/Response.archetype.json"),
          new ProtocolArchetype("RequestEffector", "protocol/http/RequestEffector.archetype.json"),
          new ProtocolArchetype(
              "ResponseEffector", "protocol/http/ResponseEffector.archetype.json"),
          new ProtocolArchetype("RequestReceptor", "protocol/http/RequestReceptor.archetype.json"),
          new ProtocolArchetype(
              "ResponseReceptor", "protocol/http/ResponseReceptor.archetype.json"),
          new ProtocolArchetype(
              "RequestInteraction", "protocol/http/RequestInteraction.archetype.json"),
          new ProtocolArchetype(
              "ResponseInteraction", "protocol/http/ResponseInteraction.archetype.json"),
          new ProtocolArchetype("RelaySignal", "protocol/relay/RelaySignal.schema.json"),
          new ProtocolArchetype("RelayEffector", "protocol/relay/RelayEffector.schema.json"),
          new ProtocolArchetype("RelayReceptor", "protocol/relay/RelayReceptor.schema.json"),
          new ProtocolArchetype("RelayInteraction", "protocol/relay/RelayInteraction.schema.json"));

  private final DefinitionManagerClient client;

  public StructureSeedRunner(DefinitionManagerClient client) {
    this.client = client;
  }

  @Override
  public void run(ApplicationArguments args) {
    log.info("Bootstrapping SIE Operator identity on Definition Manager...");

    String baseArchetypeUri = resolveBaseArchetypeUri("Archetype");
    String structureArchetypeUri = resolveBaseArchetypeUri("Structure");
    String mechanismArchetypeUri = resolveBaseArchetypeUri("Mechanism");
    String receptorArchetypeUri = resolveBaseArchetypeUri("Receptor");
    String effectorArchetypeUri = resolveBaseArchetypeUri("Effector");

    // 1. Register custom archetypes (OperationRequest, OperationResponse)
    String operationRequestArchUri =
        extractUri(
            ensureAscription(
                "ARCHETYPE",
                Map.of("title", "OperationRequest"),
                baseArchetypeUri,
                "OperationRequest.schema.json"));

    String operationResponseArchUri =
        extractUri(
            ensureAscription(
                "ARCHETYPE",
                Map.of("title", "OperationResponse"),
                baseArchetypeUri,
                "OperationResponse.schema.json"));

    // 1b. Register protocol archetypes (HTTP, Relay).
    registerArchetypes("protocol", PROTOCOL_ARCHETYPES, baseArchetypeUri);

    // 2. Register Structure
    UUID structureAscId =
        extractId(
            ensureAscription(
                "STRUCTURE",
                Map.of("purpose", "sie-operator"),
                structureArchetypeUri,
                "OperatorStructure.json"));

    // 3. Register Mechanism (resolve structure reference in statement)
    UUID mechanismAscId = ensureMechanism(mechanismArchetypeUri, structureAscId);

    // 4. Register Receptor on the mechanism
    UUID receptorAscId =
        ensurePort("RECEPTOR", receptorArchetypeUri, mechanismAscId, operationRequestArchUri);

    // 5. Register Effector on the mechanism
    UUID effectorAscId =
        ensurePort("EFFECTOR", effectorArchetypeUri, mechanismAscId, operationResponseArchUri);

    log.info(
        "SIE Operator identity registered — structure={}, mechanism={}, receptor={}, effector={}",
        structureAscId,
        mechanismAscId,
        receptorAscId,
        effectorAscId);
  }

  private String resolveBaseArchetypeUri(String title) {
    Optional<JsonNode> existing = client.findAscription("ARCHETYPE", Map.of("title", title));
    if (existing.isPresent()) {
      return extractUri(existing.get());
    }
    throw new IllegalStateException(
        "Base archetype '"
            + title
            + "' not found on Definition Manager — "
            + "ensure the Definition Manager is running and initialized");
  }

  private JsonNode ensureAscription(
      String type,
      Map<String, String> identityFilters,
      String archetypeUri,
      String statementFileName) {
    Optional<JsonNode> existing = client.findAscription(type, identityFilters);
    if (existing.isPresent()) {
      log.debug("Found existing {} ascription: {}", type, extractId(existing.get()));
      return existing.get();
    }

    JsonNode statement = loadStatement(statementFileName);
    JsonNode created = client.createAscription(archetypeUri, statement);
    log.info("Created {} ascription: {}", type, extractId(created));
    return created;
  }

  private UUID ensureMechanism(String mechanismArchetypeUri, UUID structureAscId) {
    Optional<JsonNode> existing =
        client.findAscription("MECHANISM", Map.of("function", "run-operation"));
    if (existing.isPresent()) {
      UUID id = extractId(existing.get());
      log.debug("Found existing MECHANISM ascription: {}", id);
      return id;
    }

    ObjectNode statement = (ObjectNode) loadStatement("OperatorMechanism.json");
    // Resolve template variable: replace ${sie.operator.structure.ascriptionId}
    statement.put("structure", structureAscId.toString());
    JsonNode created = client.createAscription(mechanismArchetypeUri, statement);
    UUID id = extractId(created);
    log.info("Created MECHANISM ascription: {}", id);
    return id;
  }

  private UUID ensurePort(
      String portType, String portArchetypeUri, UUID mechanismAscId, String dataArchetypeUri) {
    Optional<JsonNode> existing =
        client.findAscription(portType, Map.of("mechanism", mechanismAscId.toString()));
    if (existing.isPresent()) {
      UUID id = extractId(existing.get());
      log.debug("Found existing {} ascription: {}", portType, id);
      return id;
    }

    ObjectNode statement =
        MAPPER
            .createObjectNode()
            .put("mechanism", mechanismAscId.toString())
            .put("archetype", dataArchetypeUri);
    JsonNode created = client.createAscription(portArchetypeUri, statement);
    UUID id = extractId(created);
    log.info("Created {} ascription: {}", portType, id);
    return id;
  }

  private JsonNode loadStatement(String fileName) {
    ClassPathResource resource = new ClassPathResource(STATEMENT_DIR + fileName);
    try (InputStream is = resource.getInputStream()) {
      return MAPPER.readTree(is);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load statement file: " + fileName, e);
    }
  }

  private UUID extractId(JsonNode ascription) {
    return UUID.fromString(ascription.path("id").asText());
  }

  /** An Archetype Ascription's own URI lives in its statement's {@code $id}. */
  private String extractUri(JsonNode ascription) {
    return ascription.path("statement").path("$id").asText();
  }

  private void registerArchetypes(
      String label, List<ProtocolArchetype> archetypes, String baseArchetypeUri) {
    for (ProtocolArchetype proto : archetypes) {
      ensureAscription(
          "ARCHETYPE", Map.of("title", proto.title()), baseArchetypeUri, proto.schemaFile());
    }
    log.info("Registered {} {} archetypes", archetypes.size(), label);
  }

  private record ProtocolArchetype(String title, String schemaFile) {}
}
