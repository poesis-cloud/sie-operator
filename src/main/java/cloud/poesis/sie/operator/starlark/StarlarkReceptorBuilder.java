package cloud.poesis.sie.operator.starlark;

import java.util.Map;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkIndexable;
import net.starlark.java.eval.StarlarkSemantics;

public class StarlarkReceptorBuilder implements StarlarkIndexable {

  private final Dict<String, Object> payload;
  private boolean onCalled;

  StarlarkReceptorBuilder(Map<String, Object> triggerPayload) {
    this.payload = StarlarkSysModule.toStarlarkDict(triggerPayload);
  }

  @StarlarkMethod(
      name = "on",
      doc = "Qualify the trigger Receptor port with a specific Archetype.",
      parameters = {@Param(name = "receptor_archetype", doc = "Receptor Archetype name")})
  public StarlarkReceptorBuilder on(String receptorArchetype) throws EvalException {
    if (onCalled) {
      throw Starlark.errorf(".on() may only be called once on sys.receive()");
    }
    onCalled = true;
    // .on() is an auto-derivation hint — at runtime, just return self
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
    printer.append("<ReceptorBuilder>");
  }
}
