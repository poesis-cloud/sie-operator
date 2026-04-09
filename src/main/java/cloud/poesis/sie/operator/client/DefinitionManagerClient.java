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

  public ReceptorAscriptionDto getReceptorAscription(UUID ascriptionId) {
    return getAscription(ascriptionId, ReceptorAscriptionDto.class);
  }

  public List<EffectorAscriptionDto> findEffectors(UUID mechanismAscriptionId) {
    return findPorts("EFFECTOR", mechanismAscriptionId, EffectorAscriptionDto.class);
  }

  public List<ReceptorAscriptionDto> findReceptors(UUID mechanismAscriptionId) {
    return findPorts("RECEPTOR", mechanismAscriptionId, ReceptorAscriptionDto.class);
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
                  uriBuilder.path("/api/v1/ascriptions").queryParam("type", type);
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
    JsonNode embedded = body.path("_embedded").path("ascriptionDtoList");
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
  public JsonNode createAscription(UUID archetypeId, JsonNode statement) {
    return webClient
        .post()
        .uri("/api/v1/ascriptions")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            MAPPER
                .createObjectNode()
                .put("archetypeId", archetypeId.toString())
                .set("statement", statement))
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

  private <T> List<T> findPorts(String type, UUID mechanismAscriptionId, Class<T> elementType) {
    JsonNode body =
        webClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/api/v1/ascriptions")
                        .queryParam("type", type)
                        .queryParam("statement.mechanism", mechanismAscriptionId.toString())
                        .build())
            .retrieve()
            .bodyToMono(JsonNode.class)
            .block();
    return extractEmbeddedList(body, elementType);
  }

  private <T> List<T> extractEmbeddedList(JsonNode body, Class<T> elementType) {
    if (body == null) {
      return Collections.emptyList();
    }
    JsonNode embedded = body.path("_embedded").path("ascriptionDtoList");
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
