package cloud.poesis.sie.operator.config;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkValue;

public class MechanismRuleApiConfig implements StarlarkValue {

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
