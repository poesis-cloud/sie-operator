package cloud.poesis.sie.operator.starlark;

import java.util.Map;
import java.util.function.Function;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkIndexable;
import net.starlark.java.eval.StarlarkSemantics;

public class StarlarkEffectorBuilder implements StarlarkIndexable {

  private final String archetype;
  private final Map<String, Object> data;
  private final Function<EffectRecord, Object> dispatcher;

  private String effectorArchetype;
  private String feedbackArchetype;
  private String feedbackReceptorArchetype;
  private boolean dispatched;
  private Dict<String, Object> feedbackResult;

  StarlarkEffectorBuilder(
      String archetype, Map<String, Object> data, Function<EffectRecord, Object> dispatcher) {
    this.archetype = archetype;
    this.data = data;
    this.dispatcher = dispatcher;
  }

  @StarlarkMethod(
      name = "by",
      doc = "Qualify the Effector port with a specific Archetype.",
      parameters = {@Param(name = "effector_archetype", doc = "Effector Archetype name")})
  public StarlarkEffectorBuilder by(String effectorArchetype) throws EvalException {
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
  public StarlarkEffectorBuilder receive(String feedbackArchetype) throws EvalException {
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
  public StarlarkEffectorBuilder on(String receptorArchetype) throws EvalException {
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
      Object result = dispatcher.apply(toEffectRecord());
      if (result instanceof Map<?, ?> map) {
        feedbackResult = StarlarkSysModule.toStarlarkDict((Map<String, Object>) map);
      }
    }
  }

  EffectRecord toEffectRecord() {
    return new EffectRecord(
        archetype,
        data,
        effectorArchetype,
        feedbackArchetype,
        feedbackReceptorArchetype,
        feedbackArchetype != null);
  }

  boolean isDispatched() {
    return dispatched;
  }

  void dispatchIfPending() {
    if (!dispatched) {
      dispatched = true;
      dispatcher.apply(toEffectRecord());
    }
  }

  @Override
  public void repr(net.starlark.java.eval.Printer printer) {
    printer.append("<EffectorBuilder(" + archetype + ")>");
  }
}
