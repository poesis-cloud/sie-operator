package cloud.poesis.sie.operator.client;

import cloud.poesis.sie.operator.dto.ArchetypeAscriptionDto;
import cloud.poesis.sie.operator.dto.EffectorAscriptionDto;
import cloud.poesis.sie.operator.dto.InteractionAscriptionDto;
import cloud.poesis.sie.operator.dto.MechanismAscriptionDto;
import cloud.poesis.sie.operator.dto.ReceptorAscriptionDto;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class DefinitionManagerClient {

  private static final ObjectMapper MAPPER =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private static final Map<String, String> BASE_ARCHETYPE_URIS =
      Map.of(
          "ARCHETYPE", "gsmarc://gsm/Archetype/v1",
          "STRUCTURE", "gsmarc://gsm/Structure/v1",
          "MECHANISM", "gsmarc://gsm/Mechanism/v1",
          "EFFECTOR", "gsmarc://gsm/Effector/v1",
          "RECEPTOR", "gsmarc://gsm/Receptor/v1",
          "INTERACTION", "gsmarc://gsm/Interaction/v1",
          "DIRECTIVE", "gsmarc://gsm/Directive/v1",
          "NORM", "gsmarc://gsm/Norm/v1");

  private final WebClient webClient;

  public DefinitionManagerClient(WebClient definitionManagerWebClient) {
    this.webClient = definitionManagerWebClient.mutate().build();
  }

  // --- Read operations ---

  public MechanismAscriptionDto getMechanismAscription(UUID ascriptionId) {
    return getAscription(ascriptionId, MechanismAscriptionDto.class);
  }

  public ArchetypeAscriptionDto getArchetypeAscription(UUID ascriptionId) {
    return getAscription(ascriptionId, ArchetypeAscriptionDto.class);
  }

  public ArchetypeAscriptionDto getArchetypeAscription(String archetypeUri) {
    JsonNode body =
        webClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/api/v1/ascriptions")
                        .queryParam("type", "ARCHETYPE")
                        .queryParam("archetypeUri", typingArchetypeUri("ARCHETYPE"))
                        .queryParam("statement.$id", archetypeUri)
                        .build())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    List<ArchetypeAscriptionDto> matches = extractEmbeddedList(body, ArchetypeAscriptionDto.class);
    if (matches.size() != 1) {
      throw new IllegalStateException(
          "Expected exactly one Archetype for '" + archetypeUri + "', found " + matches.size());
    }
    ArchetypeAscriptionDto match = matches.getFirst();
    if (!archetypeUri.equals(match.uri())) {
      throw new IllegalStateException(
          "Archetype URI mismatch: requested '"
              + archetypeUri
              + "' but received '"
              + match.uri()
              + "'");
    }
    return match;
  }

  public ReceptorAscriptionDto getReceptorAscription(UUID ascriptionId) {
    JsonNode ascription = getAscription(ascriptionId, JsonNode.class);
    return toPortDto(
        ascription, resolveTypingArchetypeUri(ascriptionId), ReceptorAscriptionDto::fromJson);
  }

  public List<EffectorAscriptionDto> findEffectors(UUID mechanismAscriptionId) {
    return findPorts("EFFECTOR", mechanismAscriptionId, EffectorAscriptionDto::fromJson);
  }

  public List<ReceptorAscriptionDto> findReceptors(UUID mechanismAscriptionId) {
    return findPorts("RECEPTOR", mechanismAscriptionId, ReceptorAscriptionDto::fromJson);
  }

  public List<InteractionAscriptionDto> findActiveInteractionsForEffector(
      UUID effectorAscriptionId) {
    JsonNode body =
        webClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/api/v1/ascriptions")
                        .queryParam("type", "INTERACTION")
                        .queryParam("archetypeUri", typingArchetypeUri("INTERACTION"))
                        .queryParam("status", "ACTIVE")
                        .queryParam("statement.effector", effectorAscriptionId.toString())
                        .build())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    return extractEmbeddedList(body, InteractionAscriptionDto.class);
  }

  /**
   * Finds a single ascription by type and statement field filters. Returns the first match, or
   * empty if none found.
   */
  public Optional<JsonNode> findAscription(String type, Map<String, String> statementFilters) {
    JsonNode body =
        webClient
            .get()
            .uri(
                uriBuilder -> {
                  uriBuilder
                      .path("/api/v1/ascriptions")
                      .queryParam("type", type)
                      .queryParam("archetypeUri", typingArchetypeUri(type));
                  statementFilters.forEach(
                      (key, value) -> uriBuilder.queryParam("statement." + key, value));
                  return uriBuilder.build();
                })
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    if (body == null) {
      return Optional.empty();
    }
    JsonNode embedded = body.path("_embedded").path("ascriptions");
    if (embedded.isMissingNode() || !embedded.isArray() || embedded.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(embedded.get(0));
  }

  // --- Write operations ---

  /**
   * Creates an ascription on the Definition Manager. Returns the created ascription response
   * (including the assigned ID).
   */
  public JsonNode createAscription(String archetypeId, JsonNode statement) {
    return webClient
        .post()
        .uri("/api/v1/ascriptions")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            MAPPER.createObjectNode().put("archetypeUri", archetypeId).set("statement", statement))
        .retrieve()
        .bodyToMono(JsonNode.class)
        .block();
  }

  // --- Internal ---

  private <T> T getAscription(UUID ascriptionId, Class<T> type) {
    return webClient
        .get()
        .uri("/api/v1/ascriptions/{id}", ascriptionId)
        .retrieve()
        .bodyToMono(type)
        .block();
  }

  private <T> List<T> findPorts(String type, UUID mechanismAscriptionId, PortDtoFactory<T> mapper) {
    JsonNode body =
        webClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/api/v1/ascriptions")
                        .queryParam("type", type)
                        .queryParam("archetypeUri", typingArchetypeUri(type))
                        .queryParam("statement.mechanism", mechanismAscriptionId.toString())
                        .build())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    if (body == null) {
      return Collections.emptyList();
    }
    JsonNode embedded = body.path("_embedded").path("ascriptions");
    if (!embedded.isArray()) {
      return Collections.emptyList();
    }
    List<T> result = new ArrayList<>(embedded.size());
    for (JsonNode node : embedded) {
      UUID ascriptionId = UUID.fromString(node.path("id").asText());
      result.add(toPortDto(node, resolveTypingArchetypeUri(ascriptionId), mapper));
    }
    return result;
  }

  private static String typingArchetypeUri(String type) {
    String uri = BASE_ARCHETYPE_URIS.get(type.toUpperCase(java.util.Locale.ROOT));
    if (uri == null) {
      throw new IllegalArgumentException("Unsupported GSM subject type: " + type);
    }
    return uri;
  }

  private String resolveTypingArchetypeUri(UUID ascriptionId) {
    JsonNode schema =
        webClient
            .get()
            .uri("/api/v1/ascriptions/{id}/schema", ascriptionId)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    String archetypeUri =
        schema == null ? "" : schema.path("properties").path("statement").path("$id").asText();
    if (archetypeUri.isBlank()) {
      throw new IllegalStateException(
          "Ascription " + ascriptionId + " schema has no typing Archetype $id");
    }
    return archetypeUri;
  }

  private static <T> T toPortDto(
      JsonNode node, String portArchetypeUri, PortDtoFactory<T> factory) {
    return factory.create(
        UUID.fromString(node.path("id").asText()),
        node.path("status").asText(),
        node.path("version").asInt(),
        portArchetypeUri,
        node.path("statement"));
  }

  @FunctionalInterface
  private interface PortDtoFactory<T> {
    T create(UUID id, String status, int version, String portArchetypeUri, JsonNode statement);
  }

  private <T> List<T> extractEmbeddedList(JsonNode body, Class<T> elementType) {
    if (body == null) {
      return Collections.emptyList();
    }
    JsonNode embedded = body.path("_embedded").path("ascriptions");
    if (embedded.isMissingNode() || !embedded.isArray()) {
      return Collections.emptyList();
    }
    List<T> result = new ArrayList<>(embedded.size());
    for (JsonNode node : embedded) {
      result.add(MAPPER.convertValue(node, elementType));
    }
    return result;
  }
}
