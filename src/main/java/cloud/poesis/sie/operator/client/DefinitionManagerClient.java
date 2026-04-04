package cloud.poesis.sie.operator.client;

import cloud.poesis.sie.operator.dto.ArchetypeAscriptionDto;
import cloud.poesis.sie.operator.dto.EffectorAscriptionDto;
import cloud.poesis.sie.operator.dto.InteractionAscriptionDto;
import cloud.poesis.sie.operator.dto.MechanismAscriptionDto;
import cloud.poesis.sie.operator.dto.ReceptorAscriptionDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class DefinitionManagerClient {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final WebClient webClient;

  public DefinitionManagerClient(WebClient definitionManagerWebClient) {
    this.webClient = definitionManagerWebClient.mutate().build();
  }

  public MechanismAscriptionDto getMechanismAscription(UUID ascriptionId) {
    return getAscription(ascriptionId, MechanismAscriptionDto.class);
  }

  public ArchetypeAscriptionDto getArchetypeAscription(UUID ascriptionId) {
    return getAscription(ascriptionId, ArchetypeAscriptionDto.class);
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
