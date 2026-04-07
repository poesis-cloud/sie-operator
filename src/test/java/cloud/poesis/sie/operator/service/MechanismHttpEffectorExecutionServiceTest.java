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
  void supportsEffectsWithTargetUriAndMethod() {
    EffectDto effect =
        EffectDto.fireAndForget(
            "HttpRequest", Map.of("targetUri", "http://localhost/test", "method", "GET"));
    assertThat(dispatcher.supports(effect)).isTrue();
  }

  @Test
  void doesNotSupportEffectsWithoutTargetUri() {
    EffectDto effect = EffectDto.fireAndForget("SomeEffect", Map.of("key", "value"));
    assertThat(dispatcher.supports(effect)).isFalse();
  }

  @Test
  void doesNotSupportEffectsWithoutMethod() {
    EffectDto effect =
        EffectDto.fireAndForget("SomeEffect", Map.of("targetUri", "http://localhost/test"));
    assertThat(dispatcher.supports(effect)).isFalse();
  }

  @Test
  void doesNotSupportEffectsWithLegacyTargetURICasing() {
    EffectDto effect =
        EffectDto.fireAndForget(
            "HttpRequest", Map.of("targetURI", "http://localhost/test", "method", "GET"));
    assertThat(dispatcher.supports(effect)).isFalse();
  }

  @Test
  void dispatchesPutRequestWithBody() throws InterruptedException {
    server.enqueue(
        new MockResponse()
            .setBody("{\"status\":\"ok\"}")
            .setHeader("Content-Type", "application/json"));

    String targetUri = server.url("/api/resource").toString();
    EffectDto effect =
        EffectDto.fireAndForget(
            "HttpRequest",
            Map.of(
                "targetUri",
                targetUri,
                "method",
                "PUT",
                "contentType",
                "application/json",
                "body",
                Map.of("payload", "data")));

    Map<String, Object> result = dispatcher.dispatch(effect);

    assertThat(result).containsEntry("statusCode", 200);
    assertThat(result).containsEntry("body", "{\"status\":\"ok\"}");
    assertThat(result).containsEntry("contentType", "application/json");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getMethod()).isEqualTo("PUT");
    assertThat(request.getHeader("Content-Type")).startsWith("application/json");
  }

  @Test
  void dispatchesGetRequestWithAcceptHeader() throws InterruptedException {
    server.enqueue(
        new MockResponse().setBody("{\"items\":[]}").setHeader("Content-Type", "application/json"));

    String targetUri = server.url("/api/items").toString();
    EffectDto effect =
        EffectDto.fireAndForget(
            "HttpRequest",
            Map.of("targetUri", targetUri, "method", "GET", "accept", "application/json"));

    Map<String, Object> result = dispatcher.dispatch(effect);

    assertThat(result).containsEntry("statusCode", 200);
    assertThat(result).containsEntry("body", "{\"items\":[]}");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getHeader("Accept")).isEqualTo("application/json");
  }

  @Test
  void dispatchGetRequestDoesNotSendBody() throws InterruptedException {
    server.enqueue(new MockResponse().setBody("ok"));

    String targetUri = server.url("/api/test").toString();
    EffectDto effect =
        EffectDto.fireAndForget(
            "HttpRequest",
            Map.of("targetUri", targetUri, "method", "GET", "body", "should-be-ignored"));

    dispatcher.dispatch(effect);

    RecordedRequest request = server.takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getBodySize()).isZero();
  }

  @Test
  void dispatchReturnsStatusCodeWithoutBodyWhenEmpty() {
    server.enqueue(new MockResponse().setResponseCode(204));

    String targetUri = server.url("/api/empty").toString();
    EffectDto effect =
        EffectDto.fireAndForget("HttpRequest", Map.of("targetUri", targetUri, "method", "DELETE"));

    Map<String, Object> result = dispatcher.dispatch(effect);

    assertThat(result).containsEntry("statusCode", 204);
    assertThat(result).doesNotContainKey("body");
    assertThat(result).doesNotContainKey("contentType");
  }
}
