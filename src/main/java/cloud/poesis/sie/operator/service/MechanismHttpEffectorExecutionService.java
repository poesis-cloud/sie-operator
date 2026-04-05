package cloud.poesis.sie.operator.service;

import cloud.poesis.sie.operator.dto.EffectDto;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Order(0)
public class MechanismHttpEffectorExecutionService implements MechanismEffectorExecutionService {

  private static final Logger log =
      LoggerFactory.getLogger(MechanismHttpEffectorExecutionService.class);

  private final WebClient webClient;

  public MechanismHttpEffectorExecutionService(WebClient.Builder webClientBuilder) {
    this.webClient = webClientBuilder.build();
  }

  @Override
  public boolean supports(EffectDto effect) {
    Map<String, Object> data = effect.data();
    return data.containsKey("targetURI") && data.containsKey("method");
  }

  @Override
  public Map<String, Object> dispatch(EffectDto effect) {
    String targetUri = (String) effect.data().get("targetURI");
    String method = (String) effect.data().get("method");

    log.debug("HTTP {} → {}", method, targetUri);

    var httpMethod = org.springframework.http.HttpMethod.valueOf(method);
    var requestSpec = webClient.method(httpMethod).uri(targetUri);

    if (carriesBody(httpMethod)) {
      requestSpec.bodyValue(effect.data());
    }

    String responseBody = requestSpec.retrieve().bodyToMono(String.class).block();

    if (responseBody != null) {
      return Map.of("statusClass", "SUCCESSFUL", "body", responseBody);
    }
    return Map.of("statusClass", "SUCCESSFUL");
  }

  private static boolean carriesBody(org.springframework.http.HttpMethod method) {
    return method != org.springframework.http.HttpMethod.GET
        && method != org.springframework.http.HttpMethod.HEAD
        && method != org.springframework.http.HttpMethod.DELETE
        && method != org.springframework.http.HttpMethod.OPTIONS
        && method != org.springframework.http.HttpMethod.TRACE;
  }
}
