package net.r0kit.brijj;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class Brijj {
  public static interface Remotable {
    public Object toRemote();
  }
  public static Map<String, Object> toRemote(Object r) {
    Map<String, Object> res = new HashMap<String, Object>();
    for (Field f : r.getClass().getFields()) {
      try {
        res.put(f.getName(), f.get(r));
      } catch (IllegalAccessException ignore) {}
    }
    return res;
  }
  public static class RemoteData implements Remotable {
    public Map<String, Object> toRemote() {
      return Brijj.toRemote(this);
    }
  }
  @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME) public @interface Documentation {
    String text() default "";
  }
  @Target(ElementType.PARAMETER) @Retention(RetentionPolicy.RUNTIME) public @interface Eg {
    String value() default "";
  }
  @Target(ElementType.METHOD) @Retention(RetentionPolicy.RUNTIME) public @interface RemoteMethod {}
  @Target({ ElementType.TYPE }) @Retention(RetentionPolicy.RUNTIME) public @interface RemoteAttribute {
    String name() default "";
  }
  public static String readAllTextFrom(Reader r) throws IOException {
    StringBuilder t = new StringBuilder();
    char[] buffer = new char[4096];
    while (true) {
      int n = r.read(buffer);
      if (n <= 0) break;
      t.append(buffer, 0, n);
    }
    return t.toString();
  }
  public static String readAllTextFrom(URL url) throws IOException {
    return readAllTextFrom(new InputStreamReader(url.openConnection().getInputStream(), "UTF-8"));
  }
  public static byte[] readAllBytesFrom(InputStream i) throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    while (true) {
      int n = i.read(buffer);
      if (n <= 0) break;
      bos.write(buffer, 0, n);
    }
    return bos.toByteArray();
  }
  public static void pipe(InputStream is, OutputStream os) throws IOException {
    byte[] buffer = new byte[4096];
    while (true) {
      int n = is.read(buffer);
      if (n <= 0) break;
      os.write(buffer, 0, n);
    }
    os.flush();
  }
}
