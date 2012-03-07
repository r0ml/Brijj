
package net.r0kit.brijj.demo.simple;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.r0kit.brijj.Brijj;
import net.r0kit.brijj.RemoteRequestProxy;

public class Intro extends RemoteRequestProxy {
  public Intro(HttpServletRequest q, HttpServletResponse r) { super(q,r); }
    @Brijj.RemoteMethod public String[] getInsert() {
      return new String[] {"<p><strong style=color:red;>BriJJ tests passed</strong></p>","that is most appropriate!"};
    }
    
    @Brijj.RemoteMethod public String getError(String s) throws IOException {
      throw new IOException("invalid argument: "+s);
    }
}
