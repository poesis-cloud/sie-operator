package cloud.poesis.sie.operator.service;

import cloud.poesis.sie.operator.dto.EffectDto;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
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
    return data.containsKey("targetUri") && data.containsKey("method");
  }

  @Override
  public Map<String, Object> dispatch(EffectDto effect) {
    String targetUri = (String) effect.data().get("targetUri");
    String method = (String) effect.data().get("method");
    String contentType = (String) effect.data().get("contentType");
    String accept = (String) effect.data().get("accept");

    log.debug("HTTP {} → {}", method, targetUri);

    HttpMethod httpMethod = HttpMethod.valueOf(method);
    WebClient.RequestBodySpec requestSpec = webClient.method(httpMethod).uri(targetUri);

    if (accept != null) {
      requestSpec.accept(MediaType.parseMediaType(accept));
    }

    if (carriesBody(httpMethod) && effect.data().containsKey("body")) {
      if (contentType != null) {
        requestSpec.contentType(MediaType.parseMediaType(contentType));
      }
      requestSpec.bodyValue(effect.data().get("body"));
    }

    return requestSpec
        .exchangeToMono(
            response -> {
              return response
                  .bodyToMono(String.class)
                  .defaultIfEmpty("")
                  .map(body -> toReception(response, body));
            })
        .block();
  }

  private static Map<String, Object> toReception(ClientResponse response, String body) {
    Map<String, Object> reception = new HashMap<>();
    reception.put("statusCode", response.statusCode().value());
    MediaType responseContentType = response.headers().contentType().orElse(null);
    if (responseContentType != null) {
      reception.put("contentType", responseContentType.toString());
    }
    if (!body.isEmpty()) {
      reception.put("body", body);
    }
    return reception;
  }

  private static boolean carriesBody(HttpMethod method) {
    return method != HttpMethod.GET
        && method != HttpMethod.HEAD
        && method != HttpMethod.DELETE
        && method != HttpMethod.OPTIONS
        && method != HttpMethod.TRACE;
  }
}
