package cloud.poesis.sie.operator.config;

import cloud.poesis.sie.operator.dto.EffectDto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkFloat;
import net.starlark.java.eval.StarlarkIndexable;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkSemantics;
import net.starlark.java.eval.StarlarkValue;

/**
 * GSM Mechanism Rule causal fluent API — configures the Starlark rule sandbox environment: the
 * {@code sys} built-in module and the fluent chain types returned by {@code sys.receive()} and
 * {@code sys.effect()}.
 */
public final class MechanismRuleCausalFluentApiConfig {

  private MechanismRuleCausalFluentApiConfig() {}

  // ── Type conversion utilities (Java ↔ Starlark) ───────────────────────────

  @SuppressWarnings("unchecked")
  public static Dict<String, Object> toStarlarkDict(Map<String, Object> javaMap) {
    Map<String, Object> converted = new HashMap<>(javaMap.size());
    for (Map.Entry<String, Object> e : javaMap.entrySet()) {
      converted.put(e.getKey(), toStarlark(e.getValue()));
    }
    return Dict.immutableCopyOf(converted);
  }

  public static Object toStarlark(Object value) {
    if (value == null) {
      return Starlark.NONE;
    }
    if (value instanceof String || value instanceof Boolean) {
      return value;
    }
    if (value instanceof Integer i) {
      return StarlarkInt.of(i);
    }
    if (value instanceof Long l) {
      return StarlarkInt.of(l);
    }
    if (value instanceof Double d) {
      return StarlarkFloat.of(d);
    }
    if (value instanceof Float f) {
      return StarlarkFloat.of(f.doubleValue());
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> converted = new HashMap<>(map.size());
      for (Map.Entry<?, ?> e : map.entrySet()) {
        converted.put(e.getKey().toString(), toStarlark(e.getValue()));
      }
      return Dict.immutableCopyOf(converted);
    }
    if (value instanceof List<?> list) {
      Object[] arr = new Object[list.size()];
      for (int i = 0; i < list.size(); i++) {
        arr[i] = toStarlark(list.get(i));
      }
      return StarlarkList.immutableOf(arr);
    }
    return value.toString();
  }

  // ── sys module ─────────────────────────────────────────────────────────────

  @StarlarkBuiltin(name = "sys", doc = "Rule execution context: system identity + fluent API.")
  public static class SysModule implements StarlarkValue {

    private final String mechanismId;
    private final Map<String, Object> triggerPayload;
    private final Function<EffectDto, Object> effectHandler;
    private final List<Effect> pendingEffects = new ArrayList<>();
    private boolean receiveCalled;

    public SysModule(
        String mechanismId,
        Map<String, Object> triggerPayload,
        Function<EffectDto, Object> effectHandler) {
      this.mechanismId = mechanismId;
      this.triggerPayload = triggerPayload != null ? triggerPayload : Collections.emptyMap();
      this.effectHandler = effectHandler;
    }

    @StarlarkMethod(name = "id", doc = "The Mechanism's id (UUIDv7).", structField = true)
    public String id() {
      return mechanismId;
    }

    @StarlarkMethod(
        name = "receive",
        doc = "Declare the triggering Archetype and return its payload.",
        parameters = {@Param(name = "event_archetype", doc = "String literal — Archetype name")})
    public Reception receive(String eventArchetype) throws EvalException {
      if (receiveCalled) {
        throw Starlark.errorf("sys.receive() must be called exactly once");
      }
      receiveCalled = true;
      return new Reception(toStarlarkDict(triggerPayload));
    }

    @StarlarkMethod(
        name = "effect",
        doc = "Produce an effect typed by the named Archetype.",
        parameters = {
          @Param(name = "archetype", doc = "String literal — data Archetype name"),
          @Param(
              name = "data",
              doc = "Optional payload dict",
              defaultValue = "None",
              allowedTypes = {@ParamType(type = Dict.class), @ParamType(type = NoneType.class)})
        })
    public Effect effect(String archetype, Object data) throws EvalException {
      if (!receiveCalled) {
        throw Starlark.errorf("sys.receive() must be called before sys.effect()");
      }
      Map<String, Object> dataMap = convertDict(data);
      Effect effect = new Effect(archetype, dataMap, effectHandler);
      pendingEffects.add(effect);
      return effect;
    }

    public void flushPendingEffects() {
      for (Effect effect : pendingEffects) {
        effect.dispatchIfPending();
      }
    }

    public List<EffectDto> getEmittedEffects() {
      return pendingEffects.stream().map(Effect::toEffectDto).toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertDict(Object data) {
      if (Starlark.isNullOrNone(data)) {
        return Collections.emptyMap();
      }
      if (data instanceof Dict<?, ?> dict) {
        Map<String, Object> result = new HashMap<>(dict.size());
        for (Map.Entry<?, ?> e : dict.entrySet()) {
          result.put(e.getKey().toString(), toJava(e.getValue()));
        }
        return result;
      }
      return Collections.emptyMap();
    }

    private Object toJava(Object starlarkValue) {
      if (starlarkValue instanceof Dict<?, ?> dict) {
        Map<String, Object> map = new HashMap<>(dict.size());
        for (Map.Entry<?, ?> e : dict.entrySet()) {
          map.put(e.getKey().toString(), toJava(e.getValue()));
        }
        return map;
      }
      if (starlarkValue instanceof StarlarkList<?> list) {
        List<Object> result = new ArrayList<>(list.size());
        for (Object item : list) {
          result.add(toJava(item));
        }
        return result;
      }
      if (starlarkValue instanceof StarlarkInt starlarkInt) {
        return starlarkInt.toIntUnchecked();
      }
      if (starlarkValue instanceof StarlarkFloat starlarkFloat) {
        return starlarkFloat.toDouble();
      }
      if (starlarkValue instanceof Boolean || starlarkValue instanceof String) {
        return starlarkValue;
      }
      if (Starlark.isNullOrNone(starlarkValue)) {
        return null;
      }
      return starlarkValue.toString();
    }

    @Override
    public void repr(net.starlark.java.eval.Printer printer) {
      printer.append("<sys(" + mechanismId + ")>");
    }
  }

  // ── Reception (returned by sys.receive()) ──────────────────────────────────

  /**
   * Fluent chain returned by {@code sys.receive()}. Wraps the trigger payload and optionally
   * qualifies the Receptor port.
   */
  public static class Reception implements StarlarkIndexable {

    private final Dict<String, Object> payload;
    private boolean onCalled;

    public Reception(Dict<String, Object> payload) {
      this.payload = payload;
    }

    @StarlarkMethod(
        name = "on",
        doc = "Qualify the trigger Receptor port with a specific Archetype.",
        parameters = {@Param(name = "receptor_archetype", doc = "Receptor Archetype name")})
    public Reception on(String receptorArchetype) throws EvalException {
      if (onCalled) {
        throw Starlark.errorf(".on() may only be called once on sys.receive()");
      }
      onCalled = true;
      return this;
    }

    @Override
    public Object getIndex(StarlarkSemantics semantics, Object key) throws EvalException {
      return payload.getIndex(semantics, key);
    }

    @Override
    public boolean containsKey(StarlarkSemantics semantics, Object key) throws EvalException {
      return payload.containsKey(semantics, key);
    }

    @Override
    public void repr(net.starlark.java.eval.Printer printer) {
      printer.append("<Reception>");
    }
  }

  // ── Effect (returned by sys.effect()) ──────────────────────────────────────

  /**
   * Fluent chain returned by {@code sys.effect()}. Qualifies Effector/Receptor ports, handles lazy
   * dispatch and feedback.
   */
  public static class Effect implements StarlarkIndexable {

    private final String archetype;
    private final Map<String, Object> data;
    private final Function<EffectDto, Object> dispatcher;

    private String effectorArchetype;
    private String feedbackArchetype;
    private String feedbackReceptorArchetype;
    private boolean dispatched;
    private Dict<String, Object> feedbackResult;

    public Effect(
        String archetype, Map<String, Object> data, Function<EffectDto, Object> dispatcher) {
      this.archetype = archetype;
      this.data = data;
      this.dispatcher = dispatcher;
    }

    @StarlarkMethod(
        name = "by",
        doc = "Qualify the Effector port with a specific Archetype.",
        parameters = {@Param(name = "effector_archetype", doc = "Effector Archetype name")})
    public Effect by(String effectorArchetype) throws EvalException {
      if (this.effectorArchetype != null) {
        throw Starlark.errorf(".by() may only be called once per effect chain");
      }
      this.effectorArchetype = effectorArchetype;
      return this;
    }

    @StarlarkMethod(
        name = "receive",
        doc = "Declare a feedback Receptor (closed-loop).",
        parameters = {@Param(name = "feedback_archetype", doc = "Feedback data Archetype name")})
    public Effect receive(String feedbackArchetype) throws EvalException {
      if (this.feedbackArchetype != null) {
        throw Starlark.errorf(".receive() may only be called once per effect chain");
      }
      this.feedbackArchetype = feedbackArchetype;
      return this;
    }

    @StarlarkMethod(
        name = "on",
        doc = "Qualify the feedback Receptor port Archetype.",
        parameters = {@Param(name = "receptor_archetype", doc = "Receptor Archetype name")})
    public Effect on(String receptorArchetype) throws EvalException {
      if (this.feedbackArchetype == null) {
        throw Starlark.errorf(".on() must follow .receive() in an effect chain");
      }
      if (this.feedbackReceptorArchetype != null) {
        throw Starlark.errorf(".on() may only be called once per effect chain");
      }
      this.feedbackReceptorArchetype = receptorArchetype;
      return this;
    }

    @Override
    public Object getIndex(StarlarkSemantics semantics, Object key) throws EvalException {
      ensureDispatched();
      if (feedbackResult == null) {
        throw Starlark.errorf("Cannot index a fire-and-forget effect (no .receive() declared)");
      }
      return feedbackResult.getIndex(semantics, key);
    }

    @Override
    public boolean containsKey(StarlarkSemantics semantics, Object key) throws EvalException {
      ensureDispatched();
      if (feedbackResult == null) {
        return false;
      }
      return feedbackResult.containsKey(semantics, key);
    }

    @SuppressWarnings("unchecked")
    private void ensureDispatched() {
      if (!dispatched) {
        dispatched = true;
        Object result = dispatcher.apply(toEffectDto());
        if (result instanceof Map<?, ?> map) {
          feedbackResult = toStarlarkDict((Map<String, Object>) map);
        }
      }
    }

    public EffectDto toEffectDto() {
      return new EffectDto(
          archetype,
          data,
          effectorArchetype,
          feedbackArchetype,
          feedbackReceptorArchetype,
          feedbackArchetype != null);
    }

    public boolean isDispatched() {
      return dispatched;
    }

    public void dispatchIfPending() {
      if (!dispatched) {
        dispatched = true;
        dispatcher.apply(toEffectDto());
      }
    }

    @Override
    public void repr(net.starlark.java.eval.Printer printer) {
      printer.append("<Effect(" + archetype + ")>");
    }
  }
}
