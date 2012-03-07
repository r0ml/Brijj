
package net.r0kit.brijj.demo.simple;

import java.io.IOException;
import java.net.URL;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.r0kit.brijj.Brijj;
import net.r0kit.brijj.RemoteRequestProxy;

public class Demo extends RemoteRequestProxy {
  
  public Demo(HttpServletRequest r, HttpServletResponse s) { super(r,s); }
    @Brijj.RemoteMethod public String sayHello(String name) {
      return "Hello, " + name;
    }

    @Brijj.RemoteMethod public String getInclude() throws IOException {
        return getInclude("forward.html");
    }
    
    @Brijj.RemoteMethod public String getInclude(String x) throws IOException {
      URL u = getClass().getResource(x);
      if (u == null) throw new IOException("not found: "+x);
          
      return Brijj.readAllTextFrom(u);
    }
}
