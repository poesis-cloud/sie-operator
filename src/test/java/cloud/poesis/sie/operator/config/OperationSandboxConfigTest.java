package cloud.poesis.sie.operator.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OperationSandboxConfigTest {

  private final OperationSandboxConfig.HostFunctions functions =
      new OperationSandboxConfig.HostFunctions();

  @Test
  void nowReturnsIso8601ByDefault() throws Exception {
    String result = functions.now("");
    // ISO 8601: 2026-04-03T...Z
    assertThat(result).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+Z");
  }

  @Test
  void nowWithFormatReturnsFormattedDate() throws Exception {
    String result = functions.now("yyyy-MM-dd");
    assertThat(result).matches("\\d{4}-\\d{2}-\\d{2}");
  }

  @Test
  void uuid7ReturnsValidUuid() throws Exception {
    String result = functions.uuid7();
    assertThat(result)
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
  }

  @Test
  void uuid7ReturnsUniqueValues() throws Exception {
    String a = functions.uuid7();
    String b = functions.uuid7();
    assertThat(a).isNotEqualTo(b);
  }

  @Test
  void fullmatchReturnsTrueOnFullMatch() throws Exception {
    assertThat(functions.fullmatch("[a-z]+[0-9]+", "abc123")).isTrue();
  }

  @Test
  void fullmatchReturnsFalseOnPartialMatch() throws Exception {
    assertThat(functions.fullmatch("[a-z]+", "abc123")).isFalse();
  }

  @Test
  void fullmatchReturnsFalseOnNoMatch() throws Exception {
    assertThat(functions.fullmatch("[0-9]+", "abc")).isFalse();
  }

  @Test
  void searchReturnsFirstCaptureGroup() throws Exception {
    Object result = functions.search("v([0-9.]+)", "app-v2.3.1");
    assertThat(result).isEqualTo("2.3.1");
  }

  @Test
  void searchReturnsNoneWhenNoMatch() throws Exception {
    Object result = functions.search("v([0-9.]+)", "no-version-here");
    assertThat(result).isEqualTo(net.starlark.java.eval.Starlark.NONE);
  }
}
