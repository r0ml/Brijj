package net.r0kit.brijj;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import net.r0kit.brijj.Brijj.Documentation;
import net.r0kit.brijj.Brijj.Eg;

@MultipartConfig public class BrijjServlet extends HttpServlet {
  private static final long serialVersionUID = -8458639444465608967L;
  @Override public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
    String pathInfo = req.getPathInfo();
    if (pathInfo == null || pathInfo.length() == 0 || "/".equals(pathInfo)) doIndex(req, resp);
    else if (pathInfo.equals("/brijj.js")) doEngine(req, resp);
    else if (pathInfo.startsWith("/test/")) doTest(req, resp, pathInfo.substring("/test/".length()).replaceAll("\\.js$", ""));
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
    if (ft.size > 0) resp.setContentLength((int) ft.size);
    if (ft.filename != null) {
      resp.setHeader("Content-Disposition", (ft.inline ? "inline" : "attachment") + ";filename=" + ft.filename);
    }
    if (ft.size < 0) resp.setHeader("Transfer-Encoding", "chunked");
    else resp.setHeader("Content-Transfer-Encoding", "binary");
    resp.setHeader("Expires", "0");
    resp.setHeader("Cache-Control", "must-revalidate");
    InputStream in = null;
    OutputStream out = null;
    try {
      in = ft.getInputStream();
      out = resp.getOutputStream();
      boolean te = ft.size < 0;
      byte[] buffer = new byte[4096];
      while (true) {
        int n = in.read(buffer);
        if (n <= 0) break;
        if (te) {
          out.write(Integer.toHexString(n).getBytes());
          out.write(13);
          out.write(10);
        }
        out.write(buffer, 0, n);
        if (te) {
          out.write(13);
          out.write(10);
        }
      }
      if (te) out.write("0\r\n\r\n".getBytes());
      out.flush();
    } catch (Error er) {
      System.err.println(er);
    } finally {
      if (in != null) in.close();
      if (out != null) out.close();
    }
  }
  public void doIndex(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    StringBuilder buffer = new StringBuilder();
    buffer.append("<html>\n<head><title>Brijj Test Index</title></head>\n<body>\n");
    buffer.append("<h2>Modules known to Brijj:</h2>\n<ul>\n");
    for (String name : RemoteRequestProxy.getProxyNames()) {
      buffer.append("<li><a href='").append(req.getContextPath() + req.getServletPath());
      buffer.append("/test/").append(name).append("'>").append(name).append("</a></li>\n");
    }
    buffer.append("</ul>\n<hr>Return to <a href=\"demo/index.html\">demo home page</a></body></html>\n");
    resp.setContentType("text/html");
    resp.getWriter().print(buffer.toString());
  }
  public void doTest(HttpServletRequest req, HttpServletResponse resp, String src) throws IOException {
    String h = req.getRequestURL().toString();
    h = h.substring(0,h.length()-req.getPathInfo().length());
    resp.setContentType("text/html");
    resp.getWriter().println(generateTestPage(h, src));
  }

  public void doEngine(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    InputStream raw = null;
    String rsrc = null;
    try {
      // getClass() is incorrect here because then subclasses won't work
      raw = BrijjServlet.class.getResourceAsStream("brijj.js");
      if (raw == null) { throw new IOException("Failed to find brijj.js"); }
      rsrc = Brijj.readAllTextFrom(new InputStreamReader(raw));
    } finally {
      if (raw != null) raw.close();
    }
    rsrc = rsrc.replace("${namespace}", "window.brijj");
    long lastModified;
    URL url = BrijjServlet.class.getResource("brijj.js");
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
    out.print(rsrc);
    
    for(String rpn : RemoteRequestProxy.getProxyNames() ) {
      try {
        RemoteRequestProxy rp = RemoteRequestProxy.getModule(rpn, null, null);
      String s = rp.generateInterfaceScript(req.getContextPath() + req.getServletPath(), rpn);
        out.println(s);
      } catch(ClassNotFoundException ignore) {
        System.err.println(ignore);
      }
    }
  }
  @Override public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    try {
      String pathInfo = request.getPathInfo();
      if (pathInfo.startsWith("/download/")) doDownload(request, response,
          Integer.parseInt(pathInfo.substring("/download/".length())));
      else if (pathInfo.startsWith("/call/")) {
        new CallHandler().handle(request, response);
      } else {
        System.err.println("Page not found. pathInfo='" + request.getPathInfo() + "' requestUrl='" + request.getRequestURI() + "'");
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
      }
    } catch (Exception ex) {
      System.err.println("Error: " + ex);
      try {
        response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, "Error. Details logged to the console");
      } catch (Exception ignore) {}
    }
  }

  String rp(String s, String v, String n) {
    return s.replace("${" + v + "}", n);
  }
  public String generateTestPage(final String root, String scriptName) throws IOException {
    RemoteRequestProxy module;
    try {
      module = RemoteRequestProxy.getModule(scriptName, null, null);
    } catch (ClassNotFoundException ignore) {
      return ignore.toString();
    }
    Method[] methods = module.getMethods();
    Arrays.sort(methods, new Comparator<Method>() {
      public int compare(Method a, Method b) {
        return a.getName().compareTo(b.getName());
      }
    });
    scriptName = module.className();
    String pg = Brijj.readAllTextFrom(Brijj.class.getResource("test.html"));
    pg = rp(pg, "base",  root);
    pg = rp(pg, "moduleName", module.toString());
    StringBuffer sb = new StringBuffer();
    for (int i = 0; i < methods.length; i++) {
      Method method = methods[i];
      String methodName = method.getName();
      // Is it on the list of unusable names
      if (Json.isReserved(methodName)) {
        sb.append("<li style='color: #88A;'>" + methodName + "() is not available because it is a reserved word.</li>\n");
        continue;
      }
      sb.append("<li>\n");
      Documentation doc = method.getAnnotation(Documentation.class);
      if (doc != null) {
        sb.append("<strong>");
        sb.append(doc.text());
        sb.append("</strong><p>");
      }
      sb.append("  " + methodName + '(');
      Annotation[][] eg = method.getParameterAnnotations();
      String[] egs = new String[eg.length];
      for(int k=0;k<eg.length;k++) {
        Annotation[] egx = eg[k];
        for(Annotation egz : egx) {
          if (egz instanceof Eg) {
            egs[k]=((Eg)egz).value();
          }
        }
      }
      Class<?>[] paramTypes = method.getParameterTypes();
      for (int j = 0; j < paramTypes.length; j++) {
        Class<?> paramType = paramTypes[j];
        // The special type that we handle transparently
        String value="";
        if (egs[j] != null) value = "\"" + Json.escapeJavaScript(egs[j]) + "\"";
        else {
          value = paramType == String.class ? "\"\"" : paramType == Boolean.class || paramType == Boolean.TYPE ? "true"
              : paramType == Integer.class || paramType == Integer.TYPE || paramType == Short.class || paramType == Short.TYPE
                  || paramType == Long.class || paramType == Long.TYPE || paramType == Byte.class || paramType == Byte.TYPE ? "0"
                : paramType == Float.class || paramType == Float.TYPE || paramType == Double.class || paramType == Double.TYPE ? "0.0"
                  : paramType.isArray() || Collection.class.isAssignableFrom(paramType) ? "[]"
                    : Map.class.isAssignableFrom(paramType) ? "{}" : "";
        }
        int sz = 20;
        if (value.length() > sz) sz = value.length() + 5;
        String input = "    <input class='itext' type='text' size='" + sz + "' value='" + value + "' id='p" + i + "_" + j
            + "' title='Will be converted to: " + paramType.getName() + "'/>";
        if (paramType == BufferedImage.class || paramType == FileTransfer.class) {
          input = "    <input class='itext' type='file' id='p"+i+"_"+j+"'/>";
        }
        sb.append(input);
        sb.append(j == paramTypes.length - 1 ? "" : ", \n");
      }
      sb.append("  );\n");

      sb.append("<input class='ibutton' type='button' onclick='");
      sb.append("doClick(\"").append(scriptName).append("\",\"")
        .append(methodName).append("\",").append(Integer.toString(i)).append(",")
        .append(Integer.toString(paramTypes.length)).append(")'");

      sb.append(" value='Execute' title='Calls ")
      .append(scriptName).append(".").append(methodName)
      .append("().' /><div class=\"output\" id='d").append(Integer.toString(i))
      .append("' class='reply'></div>")
      .append("</li>\n");
    }
    pg = rp(pg, "methods", sb.toString());
    return pg;
  }
}
