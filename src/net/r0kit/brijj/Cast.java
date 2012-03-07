package net.r0kit.brijj;

import java.lang.reflect.Array;
import java.lang.reflect.Type;

public class Cast {
  public static Object cast(Object o, Type t) {
    if (t instanceof Class) return cast(o, (Class)t);
    else return null;
  }
  @SuppressWarnings("unchecked") public static <T> T cast(Object o, Class<T> c) {
    if (o == null) return null;
    Class<?> d = c;
    if (c.isPrimitive()) { d = Array.get(Array.newInstance(c, 1),0).getClass(); }
    if (d.isAssignableFrom(o.getClass())) return (T) o;
    return null;
  }
}
