
package net.r0kit.brijj.servlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.r0kit.brijj.Brijj;
import net.r0kit.brijj.CallHandler;
import net.r0kit.brijj.FileTransfer;
import net.r0kit.brijj.JavascriptOutput;
import net.r0kit.brijj.RemoteRequestProxy;

/** <p>
 * There are 5 things to do, in the order that you come across them:
 * </p>
 * <ul>
 * <li>The index test page that points at the classes</li>
 * <li>The class test page that lets you execute methods</li>
 * <li>The interface javascript that uses the engine to send requests</li>
 * <li>The engine javascript to form the iframe request and process replies</li>
 * <li>The exec 'page' that executes the method and returns data to the iframe</li>
 * </ul> */
public class BrijjServlet extends HttpServlet {
  private static final long serialVersionUID = -8458639444465608967L;

  @Override public void init(ServletConfig servletConfig) throws ServletException {
    super.init(servletConfig);
    RemoteRequestProxy.register(__System.class);
  }
  @Override public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
    String pathInfo = req.getPathInfo();
    if (pathInfo == null || pathInfo.length() == 0 || "/".equals(pathInfo)) doIndex(req, resp);
    else if (pathInfo.equals("/engine.js")) doEngine(req,resp);
    else if (pathInfo.startsWith("/interface/")) doInterface(req,resp,pathInfo.substring("/interface/".length(), pathInfo.length()-3 ));
    else if (pathInfo.startsWith("/test/")) doTest(req, resp, pathInfo.substring("/test/".length()).replaceAll("\\.js$",""));
    else if (pathInfo.startsWith("/download/")) doDownload(req, resp, Integer.parseInt(pathInfo.substring("/download/".length())));
    else doPost(req, resp);
  }
   
  public void doDownload(HttpServletRequest req, HttpServletResponse resp, int hc) throws IOException { 
    FileTransfer ft = FileTransfer.get(hc);
    if (ft == null) { 
      resp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    resp.setContentType(ft.mimeType);
//    resp.setContentType("application/octet-stream");
    
    if (ft.size>0) resp.setContentLength((int)ft.size);
    
    if (ft.filename != null) {
      resp.setHeader("Content-Disposition", (ft.inline ? "inline" : "attachment")+";filename="+ft.filename); 
//      resp.setHeader("Content-Disposition","attachment;filename="+ft.filename);
    }
    if (ft.size < 0) resp.setHeader("Transfer-Encoding", "chunked");
    else resp.setHeader("Content-Transfer-Encoding", "binary");

//    resp.setHeader("Pragma","public");
    resp.setHeader("Expires","0");
    resp.setHeader("Cache-Control","must-revalidate"); // , post-check=0, pre-check=0, private"); 
//    resp.setHeader("Cache-Control","private"); // required for certain browsers 

    // header("Content-Transfer-Encoding: binary");
    
    
    
    

    InputStream in = null;
    OutputStream out = null;
    try { in = ft.getInputStream();
    out = resp.getOutputStream();
    
    boolean te = ft.size< 0;
    byte[] buffer = new byte[4096];
    while(true) {
      int n = in.read(buffer);
      if (n <= 0) break;
      if (te) { 
        out.write( Integer.toHexString(n).getBytes());
        out.write(13);out.write(10);
      }
      out.write(buffer,0,n);
      if (te) { out.write(13);out.write(10); }
    }
    if (te) out.write("0\r\n\r\n".getBytes());
    out.flush();

    }
    catch(Error er) {
      System.err.println(er);
    }
    finally{
      if (in != null) in.close();
      if (out != null) out.close();
    }
  }
  public void doIndex(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    StringBuilder buffer = new StringBuilder();
    buffer.append("<html>\n<head><title>Brijj Test Index</title></head>\n<body>\n");
    buffer.append("<h2>Modules known to Brijj:</h2>\n<ul>\n");
    for (String name : RemoteRequestProxy.getCreatorNames()) {
      buffer.append("<li><a href='");
      buffer.append(req.getContextPath()+req.getServletPath());
      buffer.append("/test/");
      buffer.append(name);
      buffer.append("'>");
      buffer.append(name);
      buffer.append("</a></li>\n");
    }
    buffer.append("</ul>\n</body></html>\n");
    resp.setContentType("text/html");
    PrintWriter out = resp.getWriter();
    out.print(buffer.toString());
  }
  
  public void doTest(HttpServletRequest req, HttpServletResponse resp, String src) throws IOException {
    String p = generateTestPage(req.getContextPath()+req.getServletPath(),src);
    resp.setContentType("text/html");
    PrintWriter out = resp.getWriter();
    out.println(p);
  }
  
  static long engineLastModified;
  public void doEngine(HttpServletRequest req, HttpServletResponse resp) throws IOException {
     InputStream raw = null;
     String rsrc = null;
        try {
          raw = getClass().getResourceAsStream("engine.js");
          if (raw == null) { throw new IOException("Failed to find engine.js"); }
          rsrc = Brijj.readAllTextFrom(new InputStreamReader(raw));
        } finally {
          if (raw != null) raw.close();
        }

      rsrc = rsrc.replace("${contextPath}", req.getContextPath())
         .replace("${pathToBrijjServlet}", req.getContextPath()+req.getServletPath())
         .replace("${plainCallHandlerUrl}", "/call/")
         .replace("${htmlCallHandlerUrl}","/hcall/")
         .replace("${namespace}", "brijj = new function() {}; brijj.engine")
         ;

       long lastModified;
         URL url = getClass().getResource("engine.js");
         if ("file".equals(url.getProtocol())) {
           File file = new File(url.getFile());
           lastModified = file.lastModified();
         } else if ("jar".equals(url.getProtocol())) {
           lastModified = System.currentTimeMillis();
         } else lastModified = System.currentTimeMillis();


    resp.setContentType("text/javascript; charset=utf-8");
    resp.setDateHeader("Last-Modified", lastModified);
    resp.setHeader("ETag", "\"" + lastModified + '\"');

        PrintWriter out = resp.getWriter();
        out.println(rsrc);
  }
  
  @Override public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    try {
      String pathInfo = request.getPathInfo();
      
      if (pathInfo.startsWith("/download/")) doDownload(request, response, Integer.parseInt(pathInfo.substring("/download/".length())));

      else if (pathInfo.startsWith("/call/")) {
            new CallHandler().handle(request, response, false);
          }
      else if (pathInfo.startsWith("/hcall/")) { 
          new CallHandler().handle(request, response, true);
      }
      else {
        System.err.println("Page not found. pathInfo='" + request.getPathInfo() + "' requestUrl='" + request.getRequestURI() + "'");
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
      }
    } catch (Exception ex) {
      // Allow Jetty RequestRetry exception to propagate to container
      // Continuation.rethrowIfContinuation(ex);
      System.err.println("Error: " + ex);
      try {
        // We are going to act on this in engine.js so we are hoping that
        // that SC_NOT_IMPLEMENTED (501) is not something that the servers
        // use that much. I would have used something unassigned like 506+
        // But that could cause future problems and might not get through
        // proxies and the like
        response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Error. Details logged to the console");
      } catch (Exception ignore) {}
    }
  }

  public void doInterface(HttpServletRequest req, HttpServletResponse resp, String cls) throws ServletException, IOException {
    RemoteRequestProxy rp = null;
    try { rp = RemoteRequestProxy.getModule(cls, req, resp);}
    catch(ClassNotFoundException cx) { throw new ServletException(""+cx); }
    String s = rp.generateInterfaceScript(req.getContextPath()+req.getServletPath(), cls);
    resp.setContentType("text/javascript; charset=utf-8");
/*    resp.setDateHeader("Last-Modified", lastModified);
    resp.setHeader("ETag", "\"" + lastModified + '\"');
*/
        PrintWriter out = resp.getWriter();
        out.println(s);
   
  }
  String rp(String s, String v, String n) {
    // return s.replaceAll("\\$\\{" + v + "\\}", Matcher.quoteReplacement(n));
    return s.replace("${"+v+"}",n);
  }

  
  public String generateTestPage(final String root, String scriptName) throws IOException {
    // if (!container.debug) { throw new
    // SecurityException("Access to debug pages is denied."); }

    String js = ".js";
    String ihu = "/interface/";
    String interfaceURL = root + ihu + scriptName + js;
    String engineURL = root + "/engine.js";

    RemoteRequestProxy module;

    try {
      module = RemoteRequestProxy.getModule(scriptName, null, null);
    } catch (ClassNotFoundException ignore) {
      return ignore.toString();
    }
    Method[] methods = module.getMethods();
    Arrays.sort(methods, new Comparator<Method>() {
      public int compare(Method a, Method b) { return a.getName().compareTo(b.getName());}
    });
    
    scriptName = module.className();
    
    String pg = Brijj.readAllTextFrom(getClass().getResource("test.html"));

    pg = rp(pg, "engineURL", engineURL);
    pg = rp(pg, "interfaceURL", interfaceURL);
    pg = rp(pg, "moduleName", module.toString());
    pg = rp(pg, "scriptName", scriptName);

    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < methods.length; i++) {
      Method method = methods[i];
      String methodName = method.getName();

      // Is it on the list of banned names
      if (JavascriptOutput.isReserved(methodName)) {
        sb.append("<li style='color: #88A;'>" + methodName + "() is not available because it is a reserved word.</li>\n");
        continue;
      }

      sb.append("<li>\n");
      sb.append("  " + methodName + '(');

      Class<?>[] paramTypes = method.getParameterTypes();
      for (int j = 0; j < paramTypes.length; j++) {
        Class<?> paramType = paramTypes[j];

        // The special type that we handle transparently
        String value = paramType == String.class ? "\"\"" : paramType == Boolean.class || paramType == Boolean.TYPE ? "true"
          : paramType == Integer.class || paramType == Integer.TYPE || paramType == Short.class || paramType == Short.TYPE
              || paramType == Long.class || paramType == Long.TYPE || paramType == Byte.class || paramType == Byte.TYPE ? "0"
            : paramType == Float.class || paramType == Float.TYPE || paramType == Double.class || paramType == Double.TYPE ? "0.0"
              : paramType.isArray() || Collection.class.isAssignableFrom(paramType) ? "[]"
                : Map.class.isAssignableFrom(paramType) ? "{}" : "";

        sb.append("    <input class='itext' type='text' size='10' value='" + value + "' id='p" + i + j
            + "' title='Will be converted to: " + paramType.getName() + "'/>");

        sb.append(j == paramTypes.length - 1 ? "" : ", \n");
      }
      sb.append("  );\n");

      String onclick = "brijj.engine._getObject(\""+scriptName + "\")." + methodName + "(";
      for (int j = 0; j < paramTypes.length; j++) {
        onclick += "objectEval(document.getElementById(\"p" + i + j + "\").value), ";
      }
      onclick += "reply" + i + ");";

      String z = Brijj.readAllTextFrom(getClass().getResource("input.html"));

      z = rp(z, "onclick", onclick);
      z = rp(z, "scriptName", scriptName);
      z = rp(z, "methodName", methodName);
      z = rp(z, "i", Integer.toString(i));

      sb.append(z);

      // Print a warning if this method is overloaded
      boolean overloaded = false;
      for (int j = 0; j < methods.length; j++) {
        if (j != i && methods[j].getName().equals(methodName)) {
          overloaded = true;
        }
      }
      
      /*
      if (overloaded) {
        sb.append("<br/><span class='warning'>(Warning: overloaded methods are not recommended. See <a href='#overloadedMethod'>below</a>)</span>\n");
      }
*/
      
      /*
      // Print a warning if the method uses un-marshallable types
      for (Class<?> paramType1 : paramTypes) {
        if (!InboundVariable.isConvertable(paramType1)) {
          sb.append("<br/><span class='warning'>(Warning: No Converter for " + paramType1.getName()
              + ". See <a href='#missingConverter'>below</a>)</span>\n");
        }
      }
*/
      
/*      if (!OutboundVariable.isConvertable(method.getReturnType())) {
        sb.append("<br/><span class='warning'>(Warning: No Converter for " + method.getReturnType().getName()
            + ". See <a href='#missingConverter'>below</a>)</span>\n");
      }
      */

      sb.append("</li>\n");
    }

    pg = rp(pg, "methods", sb.toString());

    pg = rp(pg, "root", root);
    String help = Brijj.readAllTextFrom(getClass().getResource("help.html"));
    pg = rp(pg, "help", help);
    return pg;
  }

}
