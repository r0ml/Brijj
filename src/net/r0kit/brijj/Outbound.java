package net.r0kit.brijj;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Map;

public class Outbound implements Externalizable {
  Object theObject;
  public Outbound(Object o) {
    theObject = o;
  }
  
  public void writeExternal(ObjectOutput o) throws IOException {
    o.writeObject(theObject);
  }
  
  public void readExternal(ObjectInput i) throws IOException, ClassNotFoundException {
    theObject = i.readObject();
  }
  
  public int getParameterCount() {
    int count = 0;
    String prefix = "param";
    for (String key : ((Map<String,Object>)theObject).keySet()) {
        if (key.startsWith(prefix)) { count++; }
    }
    return count;
}

  public Object getParameter(int index) {
    String key = "param" + index;
    return ((Map<String,Object>)theObject).get(key);
  }

  public void setParameter(int i, Object z) { 
    ((Map<String,Object>)theObject).put("param"+i, z);
  }

  
}
