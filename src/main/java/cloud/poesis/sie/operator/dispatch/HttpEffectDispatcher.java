package cloud.poesis.sie.operator.dispatch;

import cloud.poesis.sie.operator.starlark.EffectRecord;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Order(0)
public class HttpEffectDispatcher implements EffectDispatcher {

  private static final Logger log = LoggerFactory.getLogger(HttpEffectDispatcher.class);

  private final WebClient webClient;

  public HttpEffectDispatcher(WebClient.Builder webClientBuilder) {
    this.webClient = webClientBuilder.build();
  }

  @Override
  public boolean supports(EffectRecord effect) {
    Map<String, Object> data = effect.data();
    return data.containsKey("targetURI") && data.containsKey("method");
  }

  @Override
  public Map<String, Object> dispatch(EffectRecord effect) {
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
