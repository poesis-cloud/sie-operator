package cloud.poesis.sie.operator.config;

import cloud.poesis.sie.operator.dto.EffectDto;
import cloud.poesis.sie.operator.service.OperationExecutionService;
import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Module;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkFloat;
import net.starlark.java.eval.StarlarkIndexable;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkSemantics;
import net.starlark.java.eval.StarlarkValue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the Starlark rule sandbox environment: declares the {@code sys} built-in module,
 * global host functions ({@code now}, {@code uuid7}, {@code fullmatch}, {@code search}), and the
 * fluent chain types returned by {@code sys.receive()} and {@code sys.effect()}.
 *
 * <p>Produces an {@link OperationExecutionService} bean wired with a sandbox factory that
 * encapsulates all Starlark environment setup.
 */
@Configuration
public class OperationSandboxConfig {

  @Bean
  OperationExecutionService operationExecutionService() {
    return new OperationExecutionService(OperationSandboxConfig::createSandbox);
  }

  public static OperationExecutionService.RuleSandbox createSandbox(
      String mechanismId,
      Map<String, Object> operationInput,
      Function<EffectDto, Object> effectHandler,
      Set<String> validReceptorDataArchetypes,
      Set<String> validEffectorDataArchetypes) {
    return createSandbox(
        mechanismId,
        operationInput,
        effectHandler,
        validReceptorDataArchetypes,
        validEffectorDataArchetypes,
        Set.of(),
        Set.of(),
        false);
  }

  public static OperationExecutionService.RuleSandbox createSandbox(
      String mechanismId,
      Map<String, Object> operationInput,
      Function<EffectDto, Object> effectHandler,
      Set<String> validReceptorDataArchetypes,
      Set<String> validEffectorDataArchetypes,
      Set<String> validReceptorPortArchetypes,
      Set<String> validEffectorPortArchetypes) {
    return createSandbox(
        mechanismId,
        operationInput,
        effectHandler,
        validReceptorDataArchetypes,
        validEffectorDataArchetypes,
        validReceptorPortArchetypes,
        validEffectorPortArchetypes,
        true);
  }

  private static OperationExecutionService.RuleSandbox createSandbox(
      String mechanismId,
      Map<String, Object> operationInput,
      Function<EffectDto, Object> effectHandler,
      Set<String> validReceptorDataArchetypes,
      Set<String> validEffectorDataArchetypes,
      Set<String> validReceptorPortArchetypes,
      Set<String> validEffectorPortArchetypes,
      boolean topologyEnforced) {

    SysModule sys =
        new SysModule(
            mechanismId,
            operationInput,
            effectHandler,
            validReceptorDataArchetypes,
            validEffectorDataArchetypes,
            validReceptorPortArchetypes,
            validEffectorPortArchetypes,
            topologyEnforced);
    HostFunctions hostFunctions = new HostFunctions();

    ImmutableMap.Builder<String, Object> predeclared = ImmutableMap.builder();
    predeclared.put("sys", sys);
    Starlark.addMethods(predeclared, hostFunctions);

    Module module = Module.withPredeclared(StarlarkSemantics.DEFAULT, predeclared.build());
    return new OperationExecutionService.RuleSandbox(
        module,
        () -> {
          sys.flushPendingEffects();
          return sys.getEmittedEffects();
        });
  }

  // ── Type conversion utilities (Java ↔ Starlark) ───────────────────────────

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

  // ── Host functions (global Starlark builtins) ──────────────────────────────

  static class HostFunctions implements StarlarkValue {

    @StarlarkMethod(
        name = "now",
        doc = "Current UTC datetime. Empty string = ISO 8601.",
        parameters = {
          @Param(
              name = "fmt",
              defaultValue = "\"\"",
              doc = "strftime-style format or empty for ISO 8601")
        })
    public String now(String fmt) {
      Instant instant = Instant.now();
      if (fmt == null || fmt.isEmpty()) {
        return DateTimeFormatter.ISO_INSTANT.format(instant);
      }
      return DateTimeFormatter.ofPattern(fmt).withZone(ZoneOffset.UTC).format(instant);
    }

    @StarlarkMethod(name = "uuid7", doc = "Generate RFC 9562 UUIDv7.")
    public String uuid7() {
      return generateUuidV7().toString();
    }

    @StarlarkMethod(
        name = "fullmatch",
        doc = "Test if entire string matches regex.",
        parameters = {
          @Param(name = "pattern", doc = "Regex pattern"),
          @Param(name = "string", doc = "String to test")
        })
    public boolean fullmatch(String pattern, String string) {
      return Pattern.matches(pattern, string);
    }

    @StarlarkMethod(
        name = "search",
        doc = "First capture group from regex search, or None.",
        parameters = {
          @Param(name = "pattern", doc = "Regex pattern with capture group"),
          @Param(name = "string", doc = "String to search")
        },
        allowReturnNones = true)
    public Object search(String pattern, String string) {
      Matcher m = Pattern.compile(pattern).matcher(string);
      if (m.find() && m.groupCount() >= 1) {
        return m.group(1);
      }
      return Starlark.NONE;
    }

    private static UUID generateUuidV7() {
      long timestamp = System.currentTimeMillis();
      long msb = (timestamp << 16) & 0xFFFFFFFFFFFF0000L;
      msb |= 0x7000L; // version 7
      msb |= (long) (Math.random() * 0x0FFF); // random bits
      long lsb = (long) (Math.random() * Long.MAX_VALUE);
      lsb = (lsb & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L; // variant 2
      return new UUID(msb, lsb);
    }
  }

  // ── sys module ─────────────────────────────────────────────────────────────

  @StarlarkBuiltin(name = "sys", doc = "Rule execution context: system identity + fluent API.")
  static class SysModule implements StarlarkValue {

    private final String mechanismId;
    private final Map<String, Object> operationInput;
    private final Function<EffectDto, Object> effectHandler;
    private final Set<String> validReceptorDataArchetypes;
    private final Set<String> validEffectorDataArchetypes;
    private final Set<String> validReceptorPortArchetypes;
    private final Set<String> validEffectorPortArchetypes;
    private final boolean topologyEnforced;
    private final List<Effect> pendingEffects = new ArrayList<>();
    private boolean receiveCalled;

    SysModule(
        String mechanismId,
        Map<String, Object> operationInput,
        Function<EffectDto, Object> effectHandler,
        Set<String> validReceptorDataArchetypes,
        Set<String> validEffectorDataArchetypes) {
      this(
          mechanismId,
          operationInput,
          effectHandler,
          validReceptorDataArchetypes,
          validEffectorDataArchetypes,
          Set.of(),
          Set.of(),
          false);
    }

    SysModule(
        String mechanismId,
        Map<String, Object> operationInput,
        Function<EffectDto, Object> effectHandler,
        Set<String> validReceptorDataArchetypes,
        Set<String> validEffectorDataArchetypes,
        Set<String> validReceptorPortArchetypes,
        Set<String> validEffectorPortArchetypes,
        boolean topologyEnforced) {
      this.mechanismId = mechanismId;
      this.operationInput = operationInput != null ? operationInput : Collections.emptyMap();
      this.effectHandler = effectHandler;
      this.validReceptorDataArchetypes =
          validReceptorDataArchetypes != null ? validReceptorDataArchetypes : Set.of();
      this.validEffectorDataArchetypes =
          validEffectorDataArchetypes != null ? validEffectorDataArchetypes : Set.of();
      this.validReceptorPortArchetypes =
          validReceptorPortArchetypes != null ? validReceptorPortArchetypes : Set.of();
      this.validEffectorPortArchetypes =
          validEffectorPortArchetypes != null ? validEffectorPortArchetypes : Set.of();
      this.topologyEnforced = topologyEnforced;
    }

    @StarlarkMethod(name = "id", doc = "The Mechanism's id (UUIDv7).", structField = true)
    public String id() {
      return mechanismId;
    }

    @StarlarkMethod(
        name = "receive",
        doc = "Declare the triggering Archetype and return its input.",
        parameters = {@Param(name = "event_archetype", doc = "String literal — Archetype name")})
    public Reception receive(String eventArchetype) throws EvalException {
      if (receiveCalled) {
        throw Starlark.errorf("sys.receive() must be called exactly once");
      }
      if (topologyEnforced && !validReceptorDataArchetypes.contains(eventArchetype)) {
        throw Starlark.errorf(
            "Unknown receptor data archetype '%s'. Valid: %s",
            eventArchetype, validReceptorDataArchetypes);
      }
      receiveCalled = true;
      return new Reception(
          toStarlarkDict(operationInput), validReceptorPortArchetypes, topologyEnforced);
    }

    @StarlarkMethod(
        name = "effect",
        doc = "Produce an effect typed by the named Archetype.",
        parameters = {
          @Param(name = "archetype", doc = "String literal — data Archetype name"),
          @Param(
              name = "data",
              doc = "Optional data dict",
              defaultValue = "None",
              allowedTypes = {@ParamType(type = Dict.class), @ParamType(type = NoneType.class)})
        })
    public Effect effect(String archetype, Object data) throws EvalException {
      if (!receiveCalled) {
        throw Starlark.errorf("sys.receive() must be called before sys.effect()");
      }
      if (topologyEnforced && !validEffectorDataArchetypes.contains(archetype)) {
        throw Starlark.errorf(
            "Unknown effector data archetype '%s'. Valid: %s",
            archetype, validEffectorDataArchetypes);
      }
      Map<String, Object> dataMap = convertDict(data);
      Effect effect =
          new Effect(
              archetype,
              dataMap,
              effectHandler,
              validReceptorDataArchetypes,
              validReceptorPortArchetypes,
              validEffectorPortArchetypes,
              topologyEnforced);
      pendingEffects.add(effect);
      return effect;
    }

    void flushPendingEffects() {
      for (Effect effect : pendingEffects) {
        effect.dispatchIfPending();
      }
    }

    List<EffectDto> getEmittedEffects() {
      return pendingEffects.stream().map(Effect::toEffectDto).toList();
    }

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

  static class Reception implements StarlarkIndexable {

    private final Dict<String, Object> input;
    private final Set<String> validPortArchetypes;
    private final boolean topologyEnforced;
    private boolean onCalled;

    Reception(
        Dict<String, Object> input, Set<String> validPortArchetypes, boolean topologyEnforced) {
      this.input = input;
      this.validPortArchetypes = validPortArchetypes;
      this.topologyEnforced = topologyEnforced;
    }

    Reception(Dict<String, Object> input) {
      this(input, Set.of(), false);
    }

    Reception(Dict<String, Object> input, Set<String> validPortArchetypes) {
      this(input, validPortArchetypes, true);
    }

    @StarlarkMethod(
        name = "on",
        doc = "Qualify the trigger Receptor port with a specific Archetype.",
        parameters = {@Param(name = "receptor_archetype", doc = "Receptor Archetype name")})
    public Reception on(String receptorArchetype) throws EvalException {
      if (onCalled) {
        throw Starlark.errorf(".on() may only be called once on sys.receive()");
      }
      if (topologyEnforced && !validPortArchetypes.contains(receptorArchetype)) {
        throw Starlark.errorf(
            "Unknown receptor port archetype '%s'. Valid: %s",
            receptorArchetype, validPortArchetypes);
      }
      onCalled = true;
      return this;
    }

    @Override
    public Object getIndex(StarlarkSemantics semantics, Object key) throws EvalException {
      return input.getIndex(semantics, key);
    }

    @Override
    public boolean containsKey(StarlarkSemantics semantics, Object key) throws EvalException {
      return input.containsKey(semantics, key);
    }

    @Override
    public void repr(net.starlark.java.eval.Printer printer) {
      printer.append("<Reception>");
    }
  }

  // ── Effect (returned by sys.effect()) ──────────────────────────────────────

  static class Effect implements StarlarkIndexable {

    private final String archetype;
    private final Map<String, Object> data;
    private final Function<EffectDto, Object> dispatcher;
    private final Set<String> validFeedbackDataArchetypes;
    private final Set<String> validReceptorPortArchetypes;
    private final Set<String> validEffectorPortArchetypes;
    private final boolean topologyEnforced;

    private String effectorArchetype;
    private String feedbackArchetype;
    private String feedbackReceptorArchetype;
    private boolean dispatched;
    private Dict<String, Object> feedbackResult;

    Effect(String archetype, Map<String, Object> data, Function<EffectDto, Object> dispatcher) {
      this(archetype, data, dispatcher, Set.of(), Set.of(), Set.of(), false);
    }

    Effect(
        String archetype,
        Map<String, Object> data,
        Function<EffectDto, Object> dispatcher,
        Set<String> validFeedbackDataArchetypes,
        Set<String> validReceptorPortArchetypes,
        Set<String> validEffectorPortArchetypes) {
      this(
          archetype,
          data,
          dispatcher,
          validFeedbackDataArchetypes,
          validReceptorPortArchetypes,
          validEffectorPortArchetypes,
          true);
    }

    Effect(
        String archetype,
        Map<String, Object> data,
        Function<EffectDto, Object> dispatcher,
        Set<String> validFeedbackDataArchetypes,
        Set<String> validReceptorPortArchetypes,
        Set<String> validEffectorPortArchetypes,
        boolean topologyEnforced) {
      this.archetype = archetype;
      this.data = data;
      this.dispatcher = dispatcher;
      this.validFeedbackDataArchetypes = validFeedbackDataArchetypes;
      this.validReceptorPortArchetypes = validReceptorPortArchetypes;
      this.validEffectorPortArchetypes = validEffectorPortArchetypes;
      this.topologyEnforced = topologyEnforced;
    }

    @StarlarkMethod(
        name = "by",
        doc = "Qualify the Effector port with a specific Archetype.",
        parameters = {@Param(name = "effector_archetype", doc = "Effector Archetype name")})
    public Effect by(String effectorArchetype) throws EvalException {
      if (this.effectorArchetype != null) {
        throw Starlark.errorf(".by() may only be called once per effect chain");
      }
      if (topologyEnforced && !validEffectorPortArchetypes.contains(effectorArchetype)) {
        throw Starlark.errorf(
            "Unknown effector port archetype '%s'. Valid: %s",
            effectorArchetype, validEffectorPortArchetypes);
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
      if (topologyEnforced && !validFeedbackDataArchetypes.contains(feedbackArchetype)) {
        throw Starlark.errorf(
            "Unknown feedback data archetype '%s'. Valid: %s",
            feedbackArchetype, validFeedbackDataArchetypes);
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
      if (topologyEnforced && !validReceptorPortArchetypes.contains(receptorArchetype)) {
        throw Starlark.errorf(
            "Unknown receptor port archetype '%s'. Valid: %s",
            receptorArchetype, validReceptorPortArchetypes);
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

    EffectDto toEffectDto() {
      return new EffectDto(
          archetype,
          data,
          effectorArchetype,
          feedbackArchetype,
          feedbackReceptorArchetype,
          feedbackArchetype != null);
    }

    void dispatchIfPending() {
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
