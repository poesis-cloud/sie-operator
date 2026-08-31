package cloud.poesis.sie.operator.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cloud.poesis.sie.operator.dto.EffectDto;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkFloat;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkSemantics;
import net.starlark.java.eval.StarlarkValue;
import org.junit.jupiter.api.Test;

class OperationSandboxConfigTest {

  private final OperationSandboxConfig.HostFunctions functions =
      new OperationSandboxConfig.HostFunctions();

  // ── HostFunctions ──────────────────────────────────────────────────────────

  @Test
  void nowReturnsIso8601ByDefault() throws Exception {
    String result = functions.now("");
    assertThat(result).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d+Z");
  }

  @Test
  void nowWithNullFormatReturnsIso8601() throws Exception {
    String result = functions.now(null);
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
    assertThat(result).isEqualTo(Starlark.NONE);
  }

  // ── toStarlark / toStarlarkDict ────────────────────────────────────────────

  @Test
  void toStarlarkConvertsNull() {
    assertThat(OperationSandboxConfig.toStarlark(null)).isEqualTo(Starlark.NONE);
  }

  @Test
  void toStarlarkPassesThroughString() {
    assertThat(OperationSandboxConfig.toStarlark("hello")).isEqualTo("hello");
  }

  @Test
  void toStarlarkPassesThroughBoolean() {
    assertThat(OperationSandboxConfig.toStarlark(true)).isEqualTo(true);
  }

  @Test
  void toStarlarkConvertsInteger() {
    Object result = OperationSandboxConfig.toStarlark(42);
    assertThat(result).isEqualTo(StarlarkInt.of(42));
  }

  @Test
  void toStarlarkConvertsLong() {
    Object result = OperationSandboxConfig.toStarlark(100L);
    assertThat(result).isEqualTo(StarlarkInt.of(100));
  }

  @Test
  void toStarlarkConvertsDouble() {
    Object result = OperationSandboxConfig.toStarlark(3.14);
    assertThat(result).isInstanceOf(StarlarkFloat.class);
  }

  @Test
  void toStarlarkConvertsFloat() {
    Object result = OperationSandboxConfig.toStarlark(2.5f);
    assertThat(result).isInstanceOf(StarlarkFloat.class);
  }

  @Test
  void toStarlarkConvertsMap() {
    Object result = OperationSandboxConfig.toStarlark(Map.of("k", "v"));
    assertThat(result).isInstanceOf(Dict.class);
  }

  @Test
  void toStarlarkConvertsList() {
    Object result = OperationSandboxConfig.toStarlark(List.of("a", 1));
    assertThat(result).isInstanceOf(StarlarkList.class);
  }

  @Test
  void toStarlarkFallsBackToString() {
    Object customObj =
        new Object() {
          @Override
          public String toString() {
            return "custom-repr";
          }
        };
    assertThat(OperationSandboxConfig.toStarlark(customObj)).isEqualTo("custom-repr");
  }

  @Test
  void toStarlarkDictConvertsAllEntries() {
    Dict<String, Object> result =
        OperationSandboxConfig.toStarlarkDict(Map.of("num", 42, "str", "val"));
    assertThat(result.get("num")).isEqualTo(StarlarkInt.of(42));
    assertThat(result.get("str")).isEqualTo("val");
  }

  // ── SysModule ──────────────────────────────────────────────────────────────

  @Test
  void sysModuleIdReturnsMechanismId() {
    var sys =
        new OperationSandboxConfig.SysModule("mech-1", Map.of(), dto -> null, Set.of(), Set.of());
    assertThat(sys.id()).isEqualTo("mech-1");
  }

  @Test
  void sysModuleReceiveReturnsReception() throws EvalException {
    var sys =
        new OperationSandboxConfig.SysModule(
            "mech-1", Map.of("k", "v"), dto -> null, Set.of(), Set.of());
    var reception = sys.receive("EventArch");
    assertThat(reception).isNotNull();
  }

  @Test
  void sysModuleReceiveCalledTwiceThrows() throws EvalException {
    var sys =
        new OperationSandboxConfig.SysModule("mech-1", Map.of(), dto -> null, Set.of(), Set.of());
    sys.receive("First");
    assertThatThrownBy(() -> sys.receive("Second"))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining("exactly once");
  }

  @Test
  void sysModuleEffectBeforeReceiveThrows() {
    var sys =
        new OperationSandboxConfig.SysModule("mech-1", Map.of(), dto -> null, Set.of(), Set.of());
    assertThatThrownBy(() -> sys.effect("Arch", Starlark.NONE))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining("sys.receive()");
  }

  @Test
  void sysModuleEffectAfterReceiveSucceeds() throws EvalException {
    var sys =
        new OperationSandboxConfig.SysModule("mech-1", Map.of(), dto -> null, Set.of(), Set.of());
    sys.receive("Event");
    var effect = sys.effect("Output", Starlark.NONE);
    assertThat(effect).isNotNull();
  }

  @Test
  void sysModuleNullOperationInputTreatedAsEmpty() throws EvalException {
    var sys = new OperationSandboxConfig.SysModule("mech-1", null, dto -> null, Set.of(), Set.of());
    var reception = sys.receive("Event");
    assertThat(reception).isNotNull();
  }

  @Test
  void sysModuleFlushAndGetEmittedEffects() throws EvalException {
    var dispatched = new ArrayList<EffectDto>();
    var sys =
        new OperationSandboxConfig.SysModule(
            "mech-1",
            Map.of(),
            dto -> {
              dispatched.add(dto);
              return null;
            },
            Set.of(),
            Set.of());
    sys.receive("Event");
    sys.effect("Output", Starlark.NONE);
    sys.flushPendingEffects();

    assertThat(dispatched).hasSize(1);
    assertThat(sys.getEmittedEffects()).hasSize(1);
    assertThat(sys.getEmittedEffects().getFirst().archetype()).isEqualTo("Output");
  }

  @Test
  void sysModuleEffectWithDictExercisesToJavaBranches() throws EvalException {
    var sys =
        new OperationSandboxConfig.SysModule("mech-1", Map.of(), dto -> null, Set.of(), Set.of());
    sys.receive("Event");

    Dict<String, Object> dict =
        Dict.immutableCopyOf(
            Map.of(
                "intVal",
                StarlarkInt.of(42),
                "floatVal",
                StarlarkFloat.of(3.14),
                "boolVal",
                true,
                "strVal",
                "hello"));
    sys.effect("Output", dict);
    sys.flushPendingEffects();

    var effects = sys.getEmittedEffects();
    assertThat(effects).hasSize(1);
    assertThat(effects.getFirst().data()).containsEntry("intVal", 42);
    assertThat(effects.getFirst().data()).containsEntry("floatVal", 3.14);
    assertThat(effects.getFirst().data()).containsEntry("boolVal", true);
    assertThat(effects.getFirst().data()).containsEntry("strVal", "hello");
  }

  @Test
  void sysModuleToJavaHandlesNestedDictAndList() throws EvalException {
    var sys =
        new OperationSandboxConfig.SysModule("mech-1", Map.of(), dto -> null, Set.of(), Set.of());
    sys.receive("Event");

    Dict<String, Object> nested = Dict.immutableCopyOf(Map.of("inner", StarlarkInt.of(99)));
    StarlarkList<Object> list = StarlarkList.immutableOf(StarlarkInt.of(1), StarlarkInt.of(2));
    Dict<String, Object> dict = Dict.immutableCopyOf(Map.of("nested", nested, "list", list));

    sys.effect("Output", dict);
    sys.flushPendingEffects();

    Map<String, Object> data = sys.getEmittedEffects().getFirst().data();
    assertThat(data.get("nested")).isInstanceOf(Map.class);
    assertThat(data.get("list")).isInstanceOf(List.class).isEqualTo(List.of(1, 2));
  }

  @Test
  void sysModuleToJavaHandlesNoneAndFallback() throws EvalException {
    var sys =
        new OperationSandboxConfig.SysModule("mech-1", Map.of(), dto -> null, Set.of(), Set.of());
    sys.receive("Event");

    StarlarkValue custom =
        new StarlarkValue() {
          @Override
          public void repr(net.starlark.java.eval.Printer printer) {
            printer.append("custom-val");
          }

          @Override
          public String toString() {
            return "custom-val";
          }
        };
    Dict<String, Object> dict =
        Dict.immutableCopyOf(Map.of("noneVal", Starlark.NONE, "customVal", custom));

    sys.effect("Output", dict);
    sys.flushPendingEffects();

    Map<String, Object> data = sys.getEmittedEffects().getFirst().data();
    assertThat(data.get("noneVal")).isNull();
    assertThat(data.get("customVal")).isEqualTo("custom-val");
  }

  @Test
  void sysModuleConvertDictWithNullReturnsEmptyMap() throws EvalException {
    var sys =
        new OperationSandboxConfig.SysModule("mech-1", Map.of(), dto -> null, Set.of(), Set.of());
    sys.receive("Event");
    sys.effect("Output", Starlark.NONE);
    sys.flushPendingEffects();

    assertThat(sys.getEmittedEffects().getFirst().data()).isEmpty();
  }

  @Test
  void sysModuleReprIncludesMechanismId() {
    var sys =
        new OperationSandboxConfig.SysModule("mech-42", Map.of(), dto -> null, Set.of(), Set.of());
    var printer = new net.starlark.java.eval.Printer();
    sys.repr(printer);
    assertThat(printer.toString()).contains("mech-42");
  }

  // ── Reception ──────────────────────────────────────────────────────────────

  @Test
  void receptionOnReturnsThis() throws EvalException {
    var dict = OperationSandboxConfig.toStarlarkDict(Map.of("key", "val"));
    var reception = new OperationSandboxConfig.Reception(dict);
    var result = reception.on("ReceptorArch");
    assertThat(result).isSameAs(reception);
  }

  @Test
  void receptionOnCalledTwiceThrows() throws EvalException {
    var dict = OperationSandboxConfig.toStarlarkDict(Map.of());
    var reception = new OperationSandboxConfig.Reception(dict);
    reception.on("First");
    assertThatThrownBy(() -> reception.on("Second"))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining(".on()");
  }

  @Test
  void receptionOnRequiresExactPortArchetypeId() throws EvalException {
    String receptorPortId = "gsmarc://test/TriggerPort/v1";
    var reception =
        new OperationSandboxConfig.Reception(
            OperationSandboxConfig.toStarlarkDict(Map.of()), Set.of(receptorPortId));

    assertThat(reception.on(receptorPortId)).isSameAs(reception);
  }

  @Test
  void receptionOnRejectsDifferentPortArchetypeVersion() {
    var reception =
        new OperationSandboxConfig.Reception(
            OperationSandboxConfig.toStarlarkDict(Map.of()),
            Set.of("gsmarc://test/TriggerPort/v1"));

    assertThatThrownBy(() -> reception.on("gsmarc://test/TriggerPort/v2"))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining("Unknown receptor port archetype");
  }

  @Test
  void receptionGetIndexReturnsValue() throws EvalException {
    var dict = OperationSandboxConfig.toStarlarkDict(Map.of("key", "val"));
    var reception = new OperationSandboxConfig.Reception(dict);
    Object result = reception.getIndex(StarlarkSemantics.DEFAULT, "key");
    assertThat(result).isEqualTo("val");
  }

  @Test
  void receptionContainsKeyReturnsTrue() throws EvalException {
    var dict = OperationSandboxConfig.toStarlarkDict(Map.of("key", "val"));
    var reception = new OperationSandboxConfig.Reception(dict);
    assertThat(reception.containsKey(StarlarkSemantics.DEFAULT, "key")).isTrue();
  }

  @Test
  void receptionContainsKeyReturnsFalse() throws EvalException {
    var dict = OperationSandboxConfig.toStarlarkDict(Map.of());
    var reception = new OperationSandboxConfig.Reception(dict);
    assertThat(reception.containsKey(StarlarkSemantics.DEFAULT, "missing")).isFalse();
  }

  @Test
  void receptionRepr() {
    var dict = OperationSandboxConfig.toStarlarkDict(Map.of());
    var reception = new OperationSandboxConfig.Reception(dict);
    var printer = new net.starlark.java.eval.Printer();
    reception.repr(printer);
    assertThat(printer.toString()).contains("Reception");
  }

  // ── Effect ─────────────────────────────────────────────────────────────────

  @Test
  void effectByReturnsThis() throws EvalException {
    var effect = new OperationSandboxConfig.Effect("Arch", Map.of(), dto -> null);
    assertThat(effect.by("EffArch")).isSameAs(effect);
  }

  @Test
  void effectByCalledTwiceThrows() throws EvalException {
    var effect = new OperationSandboxConfig.Effect("Arch", Map.of(), dto -> null);
    effect.by("First");
    assertThatThrownBy(() -> effect.by("Second"))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining(".by()");
  }

  @Test
  void effectReceiveReturnsThis() throws EvalException {
    var effect = new OperationSandboxConfig.Effect("Arch", Map.of(), dto -> null);
    assertThat(effect.receive("FeedbackArch")).isSameAs(effect);
  }

  @Test
  void effectReceiveCalledTwiceThrows() throws EvalException {
    var effect = new OperationSandboxConfig.Effect("Arch", Map.of(), dto -> null);
    effect.receive("First");
    assertThatThrownBy(() -> effect.receive("Second"))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining(".receive()");
  }

  @Test
  void effectOnWithoutReceiveThrows() {
    var effect = new OperationSandboxConfig.Effect("Arch", Map.of(), dto -> null);
    assertThatThrownBy(() -> effect.on("Receptor"))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining(".on() must follow .receive()");
  }

  @Test
  void effectOnAfterReceiveReturnsThis() throws EvalException {
    var effect = new OperationSandboxConfig.Effect("Arch", Map.of(), dto -> null);
    effect.receive("Feedback");
    assertThat(effect.on("Receptor")).isSameAs(effect);
  }

  @Test
  void effectOnCalledTwiceThrows() throws EvalException {
    var effect = new OperationSandboxConfig.Effect("Arch", Map.of(), dto -> null);
    effect.receive("Feedback");
    effect.on("First");
    assertThatThrownBy(() -> effect.on("Second"))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining(".on()");
  }

  @Test
  void effectQualifiersRequireExactDataAndPortArchetypeIds() throws EvalException {
    String feedbackDataId = "gsmarc://test/Feedback/v1";
    String receptorPortId = "gsmarc://test/FeedbackPort/v1";
    String effectorPortId = "gsmarc://test/OutputPort/v1";
    var effect =
        new OperationSandboxConfig.Effect(
            "gsmarc://test/Output/v1",
            Map.of(),
            dto -> null,
            Set.of(feedbackDataId),
            Set.of(receptorPortId),
            Set.of(effectorPortId));

    assertThat(effect.by(effectorPortId)).isSameAs(effect);
    assertThat(effect.receive(feedbackDataId)).isSameAs(effect);
    assertThat(effect.on(receptorPortId)).isSameAs(effect);
  }

  @Test
  void effectByRejectsDifferentPortArchetypeId() {
    var effect =
        new OperationSandboxConfig.Effect(
            "gsmarc://test/Output/v1",
            Map.of(),
            dto -> null,
            Set.of(),
            Set.of(),
            Set.of("gsmarc://test/OutputPort/v1"));

    assertThatThrownBy(() -> effect.by("gsmarc://other/OutputPort/v1"))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining("Unknown effector port archetype");
  }

  @Test
  void effectReceiveRejectsDifferentFeedbackDataArchetypeId() {
    var effect =
        new OperationSandboxConfig.Effect(
            "gsmarc://test/Output/v1",
            Map.of(),
            dto -> null,
            Set.of("gsmarc://test/Feedback/v1"),
            Set.of(),
            Set.of());

    assertThatThrownBy(() -> effect.receive("gsmarc://other/Feedback/v1"))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining("Unknown feedback data archetype");
  }

  @Test
  void effectOnRejectsDifferentReceptorPortArchetypeId() throws EvalException {
    var effect =
        new OperationSandboxConfig.Effect(
            "gsmarc://test/Output/v1",
            Map.of(),
            dto -> null,
            Set.of("gsmarc://test/Feedback/v1"),
            Set.of("gsmarc://test/FeedbackPort/v1"),
            Set.of());
    effect.receive("gsmarc://test/Feedback/v1");

    assertThatThrownBy(() -> effect.on("gsmarc://other/FeedbackPort/v1"))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining("Unknown receptor port archetype");
  }

  @Test
  void effectGetIndexWithoutFeedbackThrows() {
    var effect = new OperationSandboxConfig.Effect("Arch", Map.of(), dto -> null);
    assertThatThrownBy(() -> effect.getIndex(StarlarkSemantics.DEFAULT, "key"))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining("fire-and-forget");
  }

  @Test
  void effectContainsKeyWithoutFeedbackReturnsFalse() throws EvalException {
    var effect = new OperationSandboxConfig.Effect("Arch", Map.of(), dto -> null);
    assertThat(effect.containsKey(StarlarkSemantics.DEFAULT, "key")).isFalse();
  }

  @Test
  void effectGetIndexWithFeedbackReturnsValue() throws EvalException {
    var effect = new OperationSandboxConfig.Effect("Arch", Map.of(), dto -> Map.of("result", "ok"));
    effect.receive("Feedback");
    Object result = effect.getIndex(StarlarkSemantics.DEFAULT, "result");
    assertThat(result).isEqualTo("ok");
  }

  @Test
  void effectContainsKeyWithFeedbackReturnsTrue() throws EvalException {
    var effect = new OperationSandboxConfig.Effect("Arch", Map.of(), dto -> Map.of("result", "ok"));
    effect.receive("Feedback");
    assertThat(effect.containsKey(StarlarkSemantics.DEFAULT, "result")).isTrue();
  }

  @Test
  void effectDispatchIfPendingDispatchesOnce() {
    var dispatched = new ArrayList<EffectDto>();
    var effect =
        new OperationSandboxConfig.Effect(
            "Arch",
            Map.of(),
            dto -> {
              dispatched.add(dto);
              return null;
            });
    effect.dispatchIfPending();
    effect.dispatchIfPending();
    assertThat(dispatched).hasSize(1);
  }

  @Test
  void effectToEffectDtoIncludesAllFields() throws EvalException {
    var effect = new OperationSandboxConfig.Effect("DataArch", Map.of("k", "v"), dto -> null);
    effect.by("EffArch");
    effect.receive("FeedbackArch");
    effect.on("RecArch");

    EffectDto dto = effect.toEffectDto();
    assertThat(dto.archetype()).isEqualTo("DataArch");
    assertThat(dto.data()).containsEntry("k", "v");
    assertThat(dto.effectorArchetype()).isEqualTo("EffArch");
    assertThat(dto.feedbackArchetype()).isEqualTo("FeedbackArch");
    assertThat(dto.feedbackReceptorArchetype()).isEqualTo("RecArch");
    assertThat(dto.closedLoop()).isTrue();
  }

  @Test
  void effectToEffectDtoFireAndForget() {
    var effect = new OperationSandboxConfig.Effect("Arch", Map.of(), dto -> null);
    EffectDto dto = effect.toEffectDto();
    assertThat(dto.closedLoop()).isFalse();
    assertThat(dto.effectorArchetype()).isNull();
    assertThat(dto.feedbackArchetype()).isNull();
    assertThat(dto.feedbackReceptorArchetype()).isNull();
  }

  @Test
  void effectRepr() {
    var effect = new OperationSandboxConfig.Effect("MyArch", Map.of(), dto -> null);
    var printer = new net.starlark.java.eval.Printer();
    effect.repr(printer);
    assertThat(printer.toString()).contains("MyArch");
  }

  // ── createSandbox ─────────────────────────────────────────────────────────

  @Test
  void createSandboxReturnsFunctionalSandbox() {
    var sandbox =
        OperationSandboxConfig.createSandbox(
            "mech-1", Map.of("key", "val"), dto -> null, Set.of(), Set.of());
    assertThat(sandbox.module()).isNotNull();
    assertThat(sandbox.complete()).isNotNull();
  }
}
