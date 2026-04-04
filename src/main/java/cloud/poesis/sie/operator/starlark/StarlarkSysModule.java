package cloud.poesis.sie.operator.starlark;

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
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

@StarlarkBuiltin(name = "sys", doc = "Rule execution context: system identity + fluent API.")
public class StarlarkSysModule implements StarlarkValue {

  private final String mechanismId;
  private final Map<String, Object> triggerPayload;
  private final Function<EffectRecord, Object> effectHandler;
  private final List<StarlarkEffectorBuilder> pendingEffects = new ArrayList<>();
  private boolean receiveCalled;

  public StarlarkSysModule(
      String mechanismId,
      Map<String, Object> triggerPayload,
      Function<EffectRecord, Object> effectHandler) {
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
  public StarlarkReceptorBuilder receive(String eventArchetype) throws EvalException {
    if (receiveCalled) {
      throw Starlark.errorf("sys.receive() must be called exactly once");
    }
    receiveCalled = true;
    return new StarlarkReceptorBuilder(triggerPayload);
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
  public StarlarkEffectorBuilder effect(String archetype, Object data) throws EvalException {
    if (!receiveCalled) {
      throw Starlark.errorf("sys.receive() must be called before sys.effect()");
    }
    Map<String, Object> dataMap = convertDict(data);
    StarlarkEffectorBuilder builder =
        new StarlarkEffectorBuilder(archetype, dataMap, effectHandler);
    pendingEffects.add(builder);
    return builder;
  }

  public void flushPendingEffects() {
    for (StarlarkEffectorBuilder builder : pendingEffects) {
      builder.dispatchIfPending();
    }
  }

  public List<EffectRecord> getEmittedEffects() {
    return pendingEffects.stream().map(StarlarkEffectorBuilder::toEffectRecord).toList();
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
    if (starlarkValue instanceof net.starlark.java.eval.StarlarkList<?> list) {
      List<Object> result = new ArrayList<>(list.size());
      for (Object item : list) {
        result.add(toJava(item));
      }
      return result;
    }
    if (starlarkValue instanceof net.starlark.java.eval.StarlarkInt starlarkInt) {
      return starlarkInt.toIntUnchecked();
    }
    if (starlarkValue instanceof net.starlark.java.eval.StarlarkFloat starlarkFloat) {
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

  @SuppressWarnings("unchecked")
  static Dict<String, Object> toStarlarkDict(Map<String, Object> javaMap) {
    Map<String, Object> converted = new HashMap<>(javaMap.size());
    for (Map.Entry<String, Object> e : javaMap.entrySet()) {
      converted.put(e.getKey(), toStarlark(e.getValue()));
    }
    return Dict.immutableCopyOf(converted);
  }

  static Object toStarlark(Object value) {
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
}
