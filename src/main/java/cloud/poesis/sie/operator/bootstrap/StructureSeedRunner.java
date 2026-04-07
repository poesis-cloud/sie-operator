package cloud.poesis.sie.operator.bootstrap;

import cloud.poesis.sie.operator.client.DefinitionManagerClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
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

  private final DefinitionManagerClient client;

  public StructureSeedRunner(DefinitionManagerClient client) {
    this.client = client;
  }

  @Override
  public void run(ApplicationArguments args) {
    log.info("Bootstrapping SIE Operator identity on Definition Manager...");

    UUID baseArchetypeId = resolveBaseArchetypeId("Archetype");
    UUID structureArchetypeId = resolveBaseArchetypeId("Structure");
    UUID mechanismArchetypeId = resolveBaseArchetypeId("Mechanism");
    UUID receptorArchetypeId = resolveBaseArchetypeId("Receptor");
    UUID effectorArchetypeId = resolveBaseArchetypeId("Effector");

    // 1. Register custom archetypes (OperationRequest, OperationResponse)
    UUID operationRequestArchId =
        ensureAscription(
            "ARCHETYPE",
            Map.of("title", "OperationRequest"),
            baseArchetypeId,
            "OperationRequest.json");

    UUID operationResponseArchId =
        ensureAscription(
            "ARCHETYPE",
            Map.of("title", "OperationResponse"),
            baseArchetypeId,
            "OperationResponse.json");

    // 2. Register Structure
    UUID structureAscId =
        ensureAscription(
            "STRUCTURE",
            Map.of("purpose", "sie-operator"),
            structureArchetypeId,
            "OperatorStructure.json");

    // 3. Register Mechanism (resolve structure reference in statement)
    UUID mechanismAscId = ensureMechanism(mechanismArchetypeId, structureAscId);

    // 4. Register Receptor on the mechanism
    UUID receptorAscId =
        ensurePort("RECEPTOR", receptorArchetypeId, mechanismAscId, operationRequestArchId);

    // 5. Register Effector on the mechanism
    UUID effectorAscId =
        ensurePort("EFFECTOR", effectorArchetypeId, mechanismAscId, operationResponseArchId);

    log.info(
        "SIE Operator identity registered — structure={}, mechanism={}, receptor={}, effector={}",
        structureAscId,
        mechanismAscId,
        receptorAscId,
        effectorAscId);
  }

  private UUID resolveBaseArchetypeId(String title) {
    Optional<JsonNode> existing = client.findAscription("ARCHETYPE", Map.of("title", title));
    if (existing.isPresent()) {
      return extractId(existing.get());
    }
    throw new IllegalStateException(
        "Base archetype '"
            + title
            + "' not found on Definition Manager — "
            + "ensure the Definition Manager is running and initialized");
  }

  private UUID ensureAscription(
      String type,
      Map<String, String> identityFilters,
      UUID archetypeId,
      String statementFileName) {
    Optional<JsonNode> existing = client.findAscription(type, identityFilters);
    if (existing.isPresent()) {
      UUID id = extractId(existing.get());
      log.debug("Found existing {} ascription: {}", type, id);
      return id;
    }

    JsonNode statement = loadStatement(statementFileName);
    JsonNode created = client.createAscription(archetypeId, statement);
    UUID id = extractId(created);
    log.info("Created {} ascription: {}", type, id);
    return id;
  }

  private UUID ensureMechanism(UUID mechanismArchetypeId, UUID structureAscId) {
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
    JsonNode created = client.createAscription(mechanismArchetypeId, statement);
    UUID id = extractId(created);
    log.info("Created MECHANISM ascription: {}", id);
    return id;
  }

  private UUID ensurePort(
      String portType, UUID portArchetypeId, UUID mechanismAscId, UUID dataArchetypeAscId) {
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
            .put("archetype", dataArchetypeAscId.toString());
    JsonNode created = client.createAscription(portArchetypeId, statement);
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
}
