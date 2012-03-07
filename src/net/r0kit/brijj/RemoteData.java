package net.r0kit.brijj;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class RemoteData implements Remotable {
  public static Object toRemote(Object r) {
    Map<String,Object> res = new HashMap<String,Object>();
    for(Field f : r.getClass().getFields()) {
      try { res.put(f.getName(), f.get(r)); } catch(IllegalAccessException ignore) {}
    }
    return res;
  }
  public Object toRemote() { 
    return toRemote(this);
  }
}
