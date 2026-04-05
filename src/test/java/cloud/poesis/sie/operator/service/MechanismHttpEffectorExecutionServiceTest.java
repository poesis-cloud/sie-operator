package cloud.poesis.sie.operator.service;

import static org.assertj.core.api.Assertions.assertThat;

import cloud.poesis.sie.operator.dto.EffectDto;
import java.io.IOException;
import java.util.Map;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class MechanismHttpEffectorExecutionServiceTest {

  private MockWebServer server;
  private MechanismHttpEffectorExecutionService dispatcher;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();
    dispatcher = new MechanismHttpEffectorExecutionService(WebClient.builder());
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void supportsEffectsWithTargetURIAndMethod() {
    EffectDto effect =
        EffectDto.fireAndForget(
            "HTTPGetRequest", Map.of("targetURI", "http://localhost/test", "method", "GET"));
    assertThat(dispatcher.supports(effect)).isTrue();
  }

  @Test
  void doesNotSupportEffectsWithoutTargetURI() {
    EffectDto effect = EffectDto.fireAndForget("SomeEffect", Map.of("key", "value"));
    assertThat(dispatcher.supports(effect)).isFalse();
  }

  @Test
  void doesNotSupportEffectsWithoutMethod() {
    EffectDto effect =
        EffectDto.fireAndForget("SomeEffect", Map.of("targetURI", "http://localhost/test"));
    assertThat(dispatcher.supports(effect)).isFalse();
  }

  @Test
  void dispatchesPutRequest() throws InterruptedException {
    server.enqueue(
        new MockResponse()
            .setBody("{\"status\":\"ok\"}")
            .setHeader("Content-Type", "application/json"));

    String targetUri = server.url("/api/resource").toString();
    EffectDto effect =
        EffectDto.fireAndForget(
            "HTTPPutRequest", Map.of("targetURI", targetUri, "method", "PUT", "payload", "data"));

    Map<String, Object> result = dispatcher.dispatch(effect);

    assertThat(result).containsEntry("statusClass", "SUCCESSFUL");
    assertThat(result).containsKey("body");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getMethod()).isEqualTo("PUT");
  }

  @Test
  void dispatchesGetRequest() throws InterruptedException {
    server.enqueue(
        new MockResponse().setBody("{\"items\":[]}").setHeader("Content-Type", "application/json"));

    String targetUri = server.url("/api/items").toString();
    EffectDto effect =
        EffectDto.fireAndForget("HTTPGetRequest", Map.of("targetURI", targetUri, "method", "GET"));

    Map<String, Object> result = dispatcher.dispatch(effect);

    assertThat(result).containsEntry("statusClass", "SUCCESSFUL");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
  }
}
