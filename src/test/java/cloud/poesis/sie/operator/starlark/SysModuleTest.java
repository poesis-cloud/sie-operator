package cloud.poesis.sie.operator.starlark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cloud.poesis.sie.operator.config.MechanismRuleCausalFluentApiConfig;
import cloud.poesis.sie.operator.config.MechanismRuleCausalFluentApiConfig.Effect;
import cloud.poesis.sie.operator.config.MechanismRuleCausalFluentApiConfig.Reception;
import cloud.poesis.sie.operator.config.MechanismRuleCausalFluentApiConfig.SysModule;
import cloud.poesis.sie.operator.dto.EffectDto;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkFloat;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkSemantics;
import org.junit.jupiter.api.Test;

class SysModuleTest {

  private final List<EffectDto> dispatched = new ArrayList<>();

  private Object handleEffect(EffectDto record) {
    dispatched.add(record);
    if (record.closedLoop()) {
      return Map.of("approved", true, "reason", "test");
    }
    return null;
  }

  private SysModule createSys(Map<String, Object> trigger) {
    return new SysModule("mech-001", trigger, this::handleEffect);
  }

  @Test
  void idReturnsTheMechanismId() {
    SysModule sys = createSys(Map.of());
    assertThat(sys.id()).isEqualTo("mech-001");
  }

  @Test
  void receiveReturnsPayloadAccessibleByIndex() throws Exception {
    SysModule sys = createSys(Map.of("orderId", "ORD-123", "amount", 500));

    Reception result = sys.receive("PaymentFailed");

    assertThat(result.getIndex(StarlarkSemantics.DEFAULT, "orderId")).isEqualTo("ORD-123");
    assertThat(result.getIndex(StarlarkSemantics.DEFAULT, "amount"))
        .isEqualTo(net.starlark.java.eval.StarlarkInt.of(500));
  }

  @Test
  void receiveCanOnlyBeCalledOnce() throws Exception {
    SysModule sys = createSys(Map.of());
    sys.receive("PaymentFailed");

    assertThatThrownBy(() -> sys.receive("PaymentFailed"))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining("exactly once");
  }

  @Test
  void effectRequiresReceiveFirst() {
    SysModule sys = createSys(Map.of());

    assertThatThrownBy(() -> sys.effect("OrderUpdate", net.starlark.java.eval.Starlark.NONE))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining("sys.receive()");
  }

  @Test
  void fireAndForgetEffectDispatchesOnFlush() throws Exception {
    SysModule sys = createSys(Map.of());
    sys.receive("Trigger");
    sys.effect("OrderShipped", net.starlark.java.eval.Starlark.NONE);

    assertThat(dispatched).isEmpty();

    sys.flushPendingEffects();

    assertThat(dispatched).hasSize(1);
    assertThat(dispatched.getFirst().archetype()).isEqualTo("OrderShipped");
    assertThat(dispatched.getFirst().closedLoop()).isFalse();
  }

  @Test
  void closedLoopEffectDispatchesOnIndexAccess() throws Exception {
    SysModule sys = createSys(Map.of());
    sys.receive("Trigger");
    Effect effect = sys.effect("ValidatePayment", net.starlark.java.eval.Starlark.NONE);
    effect.receive("PaymentValidated");

    // Not dispatched yet
    assertThat(dispatched).isEmpty();

    // Access triggers dispatch
    Object approved = effect.getIndex(StarlarkSemantics.DEFAULT, "approved");

    assertThat(dispatched).hasSize(1);
    assertThat(dispatched.getFirst().closedLoop()).isTrue();
    assertThat(dispatched.getFirst().feedbackArchetype()).isEqualTo("PaymentValidated");
    assertThat(approved).isEqualTo(true);
  }

  @Test
  void fullChainWithByReceiveOn() throws Exception {
    SysModule sys = createSys(Map.of());
    sys.receive("Trigger");

    Effect effect = sys.effect("FraudCheck", net.starlark.java.eval.Starlark.NONE);
    effect.by("FraudGateway");
    effect.receive("FraudResult");
    effect.on("FraudReceiver");

    Object result = effect.getIndex(StarlarkSemantics.DEFAULT, "approved");

    assertThat(dispatched).hasSize(1);
    EffectDto record = dispatched.getFirst();
    assertThat(record.archetype()).isEqualTo("FraudCheck");
    assertThat(record.effectorArchetype()).isEqualTo("FraudGateway");
    assertThat(record.feedbackArchetype()).isEqualTo("FraudResult");
    assertThat(record.feedbackReceptorArchetype()).isEqualTo("FraudReceiver");
    assertThat(record.closedLoop()).isTrue();
    assertThat(result).isEqualTo(true);
  }

  @Test
  void byCanOnlyBeCalledOnce() throws Exception {
    SysModule sys = createSys(Map.of());
    sys.receive("T");
    Effect effect = sys.effect("A", net.starlark.java.eval.Starlark.NONE);
    effect.by("E1");

    assertThatThrownBy(() -> effect.by("E2"))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining("once");
  }

  @Test
  void receiveOnBuilderCanOnlyBeCalledOnce() throws Exception {
    SysModule sys = createSys(Map.of());
    sys.receive("T");
    Effect effect = sys.effect("A", net.starlark.java.eval.Starlark.NONE);
    effect.receive("F1");

    assertThatThrownBy(() -> effect.receive("F2"))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining("once");
  }

  @Test
  void onWithoutReceiveThrows() throws Exception {
    SysModule sys = createSys(Map.of());
    sys.receive("T");
    Effect effect = sys.effect("A", net.starlark.java.eval.Starlark.NONE);

    assertThatThrownBy(() -> effect.on("R"))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining(".receive()");
  }

  @Test
  void getEmittedEffectsReturnsAllRecords() throws Exception {
    SysModule sys = createSys(Map.of());
    sys.receive("T");
    sys.effect("Effect1", net.starlark.java.eval.Starlark.NONE);
    sys.effect("Effect2", net.starlark.java.eval.Starlark.NONE);

    List<EffectDto> effects = sys.getEmittedEffects();

    assertThat(effects).hasSize(2);
    assertThat(effects.get(0).archetype()).isEqualTo("Effect1");
    assertThat(effects.get(1).archetype()).isEqualTo("Effect2");
  }

  @Test
  void receptionOnCanOnlyBeCalledOnce() throws Exception {
    SysModule sys = createSys(Map.of("k", "v"));
    Reception rb = sys.receive("T");
    rb.on("R1");

    assertThatThrownBy(() -> rb.on("R2"))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining("once");
  }

  @Test
  void receptionContainsKey() throws Exception {
    SysModule sys = createSys(Map.of("existing", "val"));
    Reception rb = sys.receive("T");

    assertThat(rb.containsKey(StarlarkSemantics.DEFAULT, "existing")).isTrue();
    assertThat(rb.containsKey(StarlarkSemantics.DEFAULT, "missing")).isFalse();
  }

  // --- toStarlark conversion tests ---

  @Test
  void toStarlarkConvertsNull() {
    assertThat(MechanismRuleCausalFluentApiConfig.toStarlark(null)).isEqualTo(Starlark.NONE);
  }

  @Test
  void toStarlarkConvertsStringAndBoolean() {
    assertThat(MechanismRuleCausalFluentApiConfig.toStarlark("hello")).isEqualTo("hello");
    assertThat(MechanismRuleCausalFluentApiConfig.toStarlark(true)).isEqualTo(true);
    assertThat(MechanismRuleCausalFluentApiConfig.toStarlark(false)).isEqualTo(false);
  }

  @Test
  void toStarlarkConvertsIntegerAndLong() {
    assertThat(MechanismRuleCausalFluentApiConfig.toStarlark(42)).isEqualTo(StarlarkInt.of(42));
    assertThat(MechanismRuleCausalFluentApiConfig.toStarlark(123L)).isEqualTo(StarlarkInt.of(123));
  }

  @Test
  void toStarlarkConvertsDoubleAndFloat() {
    assertThat(MechanismRuleCausalFluentApiConfig.toStarlark(3.14))
        .isEqualTo(StarlarkFloat.of(3.14));
    assertThat(MechanismRuleCausalFluentApiConfig.toStarlark(2.5f))
        .isEqualTo(StarlarkFloat.of(2.5));
  }

  @Test
  void toStarlarkConvertsNestedMap() {
    Map<String, Object> nested = Map.of("inner", 10);
    Map<String, Object> outer = Map.of("nested", nested, "name", "test");
    Object result = MechanismRuleCausalFluentApiConfig.toStarlark(outer);
    assertThat(result).isInstanceOf(Dict.class);
  }

  @Test
  void toStarlarkConvertsList() {
    List<Object> list = List.of(1, "two", 3.0);
    Object result = MechanismRuleCausalFluentApiConfig.toStarlark(list);
    assertThat(result).isInstanceOf(StarlarkList.class);
  }

  @Test
  void toStarlarkConvertsUnknownToString() {
    Object custom = new StringBuilder("custom");
    assertThat(MechanismRuleCausalFluentApiConfig.toStarlark(custom)).isEqualTo("custom");
  }

  @Test
  void toStarlarkDictConvertsJavaMap() throws Exception {
    Map<String, Object> map = new HashMap<>();
    map.put("name", "test");
    map.put("count", 42);
    map.put("ratio", 0.5);
    Dict<String, Object> dict = MechanismRuleCausalFluentApiConfig.toStarlarkDict(map);
    assertThat(dict.containsKey(StarlarkSemantics.DEFAULT, "name")).isTrue();
    assertThat(dict.containsKey(StarlarkSemantics.DEFAULT, "count")).isTrue();
    assertThat(dict.containsKey(StarlarkSemantics.DEFAULT, "ratio")).isTrue();
  }

  // --- toJava conversion via effect data round-trip ---

  @Test
  void effectWithDictDataConvertsToJavaMap() throws Exception {
    SysModule sys = createSys(Map.of());
    sys.receive("T");
    Dict<String, Object> starlarkDict =
        Dict.immutableCopyOf(
            Map.of(
                "name",
                "test",
                "count",
                StarlarkInt.of(42),
                "ratio",
                StarlarkFloat.of(3.14),
                "active",
                true));
    sys.effect("A", starlarkDict);
    sys.flushPendingEffects();

    List<EffectDto> effects = sys.getEmittedEffects();
    assertThat(effects).hasSize(1);
    Map<String, Object> data = effects.getFirst().data();
    assertThat(data.get("name")).isEqualTo("test");
    assertThat(data.get("count")).isEqualTo(42);
    assertThat(data.get("ratio")).isEqualTo(3.14);
    assertThat(data.get("active")).isEqualTo(true);
  }

  @Test
  void effectWithNestedDictConvertsRecursively() throws Exception {
    SysModule sys = createSys(Map.of());
    sys.receive("T");
    Dict<String, Object> inner = Dict.immutableCopyOf(Map.of("x", StarlarkInt.of(1)));
    Dict<String, Object> outer = Dict.immutableCopyOf(Map.of("nested", inner));
    sys.effect("A", outer);
    sys.flushPendingEffects();

    Map<String, Object> data = sys.getEmittedEffects().getFirst().data();
    assertThat(data.get("nested")).isInstanceOf(Map.class);
    @SuppressWarnings("unchecked")
    Map<String, Object> nestedMap = (Map<String, Object>) data.get("nested");
    assertThat(nestedMap.get("x")).isEqualTo(1);
  }

  @Test
  void effectWithStarlarkListConvertsToJavaList() throws Exception {
    SysModule sys = createSys(Map.of());
    sys.receive("T");
    StarlarkList<Object> list =
        StarlarkList.immutableOf(StarlarkInt.of(1), StarlarkInt.of(2), StarlarkInt.of(3));
    Dict<String, Object> dict = Dict.immutableCopyOf(Map.of("items", list));
    sys.effect("A", dict);
    sys.flushPendingEffects();

    Object items = sys.getEmittedEffects().getFirst().data().get("items");
    assertThat(items).isInstanceOf(List.class);
    @SuppressWarnings("unchecked")
    List<Object> itemList = (List<Object>) items;
    assertThat(itemList).containsExactly(1, 2, 3);
  }

  @Test
  void effectWithNoneValueConvertsToNull() throws Exception {
    SysModule sys = createSys(Map.of());
    sys.receive("T");
    Map<String, Object> raw = new HashMap<>();
    raw.put("value", Starlark.NONE);
    Dict<String, Object> dict = Dict.immutableCopyOf(raw);
    sys.effect("A", dict);
    sys.flushPendingEffects();

    Map<String, Object> data = sys.getEmittedEffects().getFirst().data();
    assertThat(data).containsKey("value");
    assertThat(data.get("value")).isNull();
  }

  // --- repr ---

  @Test
  void sysModuleRepr() {
    SysModule sys = createSys(Map.of());
    net.starlark.java.eval.Printer printer = new net.starlark.java.eval.Printer();
    sys.repr(printer);
    assertThat(printer.toString()).contains("mech-001");
  }

  @Test
  void receptionRepr() throws Exception {
    SysModule sys = createSys(Map.of());
    Reception rb = sys.receive("T");
    net.starlark.java.eval.Printer printer = new net.starlark.java.eval.Printer();
    rb.repr(printer);
    assertThat(printer.toString()).isNotEmpty();
  }

  @Test
  void effectRepr() throws Exception {
    SysModule sys = createSys(Map.of());
    sys.receive("T");
    Effect eb = sys.effect("A", Starlark.NONE);
    net.starlark.java.eval.Printer printer = new net.starlark.java.eval.Printer();
    eb.repr(printer);
    assertThat(printer.toString()).contains("A");
  }

  @Test
  void effectGetIndexWithoutReceiveThrows() throws Exception {
    SysModule sys = createSys(Map.of());
    sys.receive("T");
    Effect effect = sys.effect("A", Starlark.NONE);

    assertThatThrownBy(() -> effect.getIndex(StarlarkSemantics.DEFAULT, "key"))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining("fire-and-forget");
  }

  @Test
  void effectContainsKeyWithoutReceiveReturnsFalse() throws Exception {
    SysModule sys = createSys(Map.of());
    sys.receive("T");
    Effect effect = sys.effect("A", Starlark.NONE);

    assertThat(effect.containsKey(StarlarkSemantics.DEFAULT, "key")).isFalse();
  }

  @Test
  void effectByOnceConstraint() throws Exception {
    SysModule sys = createSys(Map.of());
    sys.receive("T");
    Effect effect = sys.effect("A", Starlark.NONE);
    effect.by("E1");

    assertThatThrownBy(() -> effect.by("E2"))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining("once");
  }

  @Test
  void effectOnOnceConstraint() throws Exception {
    SysModule sys = createSys(Map.of());
    sys.receive("T");
    Effect effect = sys.effect("A", Starlark.NONE);
    effect.receive("F");
    effect.on("R1");

    assertThatThrownBy(() -> effect.on("R2"))
        .isInstanceOf(EvalException.class)
        .hasMessageContaining("once");
  }

  @Test
  void createSysWithNullTriggerPayload() throws Exception {
    SysModule sys = new SysModule("m1", null, this::handleEffect);
    Reception rb = sys.receive("T");
    assertThat(rb.containsKey(StarlarkSemantics.DEFAULT, "anything")).isFalse();
  }
}
