package net.r0kit.brijj;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import net.r0kit.brijj.Cast.CastException;
import net.r0kit.brijj.RemoteRequestProxy.Documentation;
import net.r0kit.brijj.RemoteRequestProxy.Eg;
import net.r0kit.brijj.RemoteRequestProxy.PreLogin;

@MultipartConfig public class BrijjServlet extends HttpServlet {
  private static Logger logger = Logger.getLogger("BrijjServlet");
  
  static {
    javax.imageio.spi.IIORegistry.getDefaultInstance().registerServiceProvider(new com.sun.media.imageioimpl.plugins.tiff.TIFFImageReaderSpi());
  }
  
  private static final long serialVersionUID = -8458639444465608967L;
  @Override public void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
    String pathInfo = req.getPathInfo();
    if (pathInfo == null || pathInfo.length() == 0 || "/".equals(pathInfo)) doIndex(req, resp);
    else if (pathInfo.equals("/brijj.js")) doEngine(req, resp, false);
    else if (pathInfo.equals("/aBrijj.js")) doEngine(req, resp, true);
    else if (pathInfo.startsWith("/test/")) doTest(req, resp, pathInfo.substring("/test/".length()).replaceAll("\\.js$", ""));
    else if (pathInfo.startsWith("/download/")) doDownload(req, resp, Integer.parseInt(pathInfo.substring("/download/".length())));
    else if (pathInfo.startsWith("/call/")) {
      String[] pi = pathInfo.split("/");
      String[] pix = new String[pi.length-3];
      System.arraycopy(pi, 3, pix, 0, pix.length);
      doTheGet(req, resp, pi[2], pix);
    }
    else doPost(req, resp);
  }
  @SuppressWarnings("serial") public static class NotLoggedIn extends RuntimeException {
    @Override public String toString() { return "Not Logged In"; }
    @Override public String getMessage() { return "Not Logged In"; }
  }
  public Object invoker(String mth, Object[] ov, HttpServletRequest req, HttpServletResponse resp) {
    Object rsp;
    RemoteRequestProxy object = null;
    try {
      String[] smns = mth.split("\\.");
      String clazz = smns[0];
      Object[] rna = findMethod(ov, clazz, smns[1]);
      Method method = (Method)rna[0];
      ov = (Object[]) rna[1];
      if (method == null) { throw new IllegalArgumentException("Missing method or missing parameter converters"); }
      
      if (method.isAnnotationPresent(PreLogin.class) || req.getSession().getAttribute(RemoteRequestProxy.loginAttribute) != null);
      else return new NotLoggedIn();
      
      // Convert all the parameters to the correct types
      int destParamCount = method.getParameterTypes().length;
      Object[] arguments = new Object[destParamCount];
      for (int j = 0; j < destParamCount; j++) {
        Object param = ov[j];
        Type paramType = method.getGenericParameterTypes()[j];
        arguments[j] = Cast.cast(paramType, param);
      }
      object = RemoteRequestProxy.getModule(clazz, req, resp);
      Object res = method.invoke(object, arguments);
      rsp = res;
    } catch (InvocationTargetException itx) {
      rsp = itx.getTargetException();
      if (object != null) object.logError(mth, (Throwable)rsp);
      logger.log(Level.SEVERE, ((Throwable)rsp).getLocalizedMessage());
    } catch (Throwable ex) {
      rsp = ex;
      logger.log(Level.SEVERE, ((Throwable)rsp).getLocalizedMessage());
    }
    if (rsp instanceof BufferedImage) rsp = new FileTransfer((BufferedImage) rsp, "png");
    return rsp;
  }
  // TODO: set headers to prevent caching of this URL so that the call will be re-issued instead of read from cache
  public void doTheGet(HttpServletRequest req, HttpServletResponse resp,String mth, String[] pix) throws IOException {
    Map<String,String[]> mm = req.getParameterMap();
    Map<String,String> mx = new HashMap<String,String>();
    for( Entry<String,String[]> kv : mm.entrySet()) {
      mx.put(kv.getKey(), kv.getValue()[0]);
    }
    
    Object rsp = invoker(mth, new Object[] {pix, mx}, req, resp);
    // handle
    
    writeHTML(resp, rsp);
  }
  
  public void writeHTML(HttpServletResponse resp, Object rsp) throws IOException {
    if (rsp instanceof String) {
      resp.setContentType("text/html");
      resp.getWriter().write( (String)rsp);
      return;
    }
    resp.getWriter().write( rsp.toString());
  }
  public void doDownload(HttpServletRequest req, HttpServletResponse resp, int hc) throws IOException {
    FileTransfer ft = FileTransfer.get(hc);
    if (ft == null) {
      resp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    resp.setContentType(ft.mimeType);
    if (ft.size > 0) resp.setContentLength((int) ft.size);
    resp.setHeader("Content-Disposition",(ft.inline ? "inline" : "attachment") + (ft.filename != null ? (";filename=\""+ft.filename.trim().replace("\"", "")+"\"") : ""));
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
      logger.log(Level.SEVERE, er.getLocalizedMessage());
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
    h = h.substring(0, h.length() - req.getPathInfo().length());
    
    int colon = h.indexOf(':');
    if (colon >= 0) h = h.substring(colon+1);
    
    resp.setContentType("text/html");
    resp.getWriter().println(generateTestPage(h, src));
  }
  public void doEngine(HttpServletRequest req, HttpServletResponse resp, boolean angular) throws IOException {
    InputStream raw = null;
    String rsrc = null;
    try {
      // getClass() is incorrect here because then subclasses won't work
      String ss = angular ? "aBrijj.js" : "brijj.js";
      raw = BrijjServlet.class.getResourceAsStream(ss);
      if (raw == null) { throw new IOException("Failed to find "+ss); }
      rsrc = readAllTextFrom(new InputStreamReader(raw));
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
    
    StringBuffer sb = new StringBuffer();
    if (angular) {
      /* FIXME:  This only works for a single Brijj Servlet */
      for (String rpn : RemoteRequestProxy.getProxyNames()) {
        rsrc = rsrc.replace("{{scriptName}}", rpn);
        sb.append(generateInterfaceScriptAngular(req.getContextPath() + req.getServletPath(), rpn));
        sb.append("\n\n");
      }
    } else {
      for (String rpn : RemoteRequestProxy.getProxyNames()) {
        sb.append(generateInterfaceScript(req.getContextPath() + req.getServletPath(), rpn));
        sb.append("\n\n");
      }
    }
    rsrc = rsrc.replace("{{functions}}", sb.toString());
    out.print(rsrc);
  }
  
  public String generateInterfaceScript(String csp, String scriptName) {
    RemoteRequestProxy module;
    try {
      module = RemoteRequestProxy.getModule(scriptName, null, null);
    } catch (ClassNotFoundException ignore) {
      logger.log(Level.SEVERE, ignore.toString());
      return "";
    }
    StringBuilder buffer = new StringBuilder();
    // defines the java classes in the global context
    buffer.append("\n(function() {  var _ = window; if (_." + scriptName + " == undefined) {\n    var p;");
    buffer.append("p = {}; p._path = '" + csp + "';\n");
    for (Method method : module.getMethodList()) {
      String methodName = method.getName();
      // Is it on the list of banned names
      if (Json.isReserved(methodName)) continue;
      Class<?>[] paramTypes = method.getParameterTypes();
      // Create the function definition
      buffer.append("p." + methodName + " = function(  ");
      for (int j = 0; j < paramTypes.length; j++)
        buffer.append("p").append(j).append(", ");
      buffer.setLength(buffer.length()-2);
      buffer.append(") {\n  return brijj._execute(p._path, '");
      buffer.append(scriptName);
      buffer.append("', '");
      buffer.append(methodName);
      buffer.append("\', arguments); };\n");
    }
    buffer.append("    _." + scriptName + "=p; } })();\n");
    return buffer.toString();
  }


  public String generateInterfaceScriptAngular(String csp, String scriptName) {
    RemoteRequestProxy module;
    try {
      module = RemoteRequestProxy.getModule(scriptName, null, null);
    } catch (ClassNotFoundException ignore) {
      logger.log(Level.SEVERE, ignore.toString());
      return "";
    }
    StringBuilder buffer = new StringBuilder();
    // defines the java classes in the global context
    for (Method method : module.getMethodList()) {
      String methodName = method.getName();
      // Is it on the list of banned names
      if (Json.isReserved(methodName)) continue;
      Class<?>[] paramTypes = method.getParameterTypes();
      // Create the function definition
      buffer.append("     "+methodName + ": function(  ");
      for (int j = 0; j < paramTypes.length; j++)
        buffer.append("p").append(j).append(", ");
      buffer.setLength(buffer.length()-2);
      buffer.append(") {\n  return this._execute('");
      buffer.append(scriptName);
      buffer.append("', '");
      buffer.append(methodName);
      buffer.append("\', arguments); },\n");
    }
    return buffer.toString();
  }


  
  
  
  @Override public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
    try {
      String pathInfo = request.getPathInfo();
      if (pathInfo.startsWith("/download/")) doDownload(request, response,
          Integer.parseInt(pathInfo.substring("/download/".length())));
      else if (pathInfo.startsWith("/call/")) {
        handle(request, response);
      } else {
        logger.log(Level.SEVERE, "Page not found. pathInfo='" + request.getPathInfo() + "' requestUrl='" + request.getRequestURI() + "'");
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
      }
    } catch (Exception ex) {
      logger.log(Level.SEVERE, ex.toString());
      try {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.getWriter().write(ex.getLocalizedMessage());
        // response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED, ex.getLocalizedMessage());
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
    scriptName = module.getClass().getSimpleName();
    String pg = readAllTextFrom(getClass().getResource("test.html"));
    pg = rp(pg, "base", root);
    pg = rp(pg, "moduleName", module.toString());
    StringBuffer sb = new StringBuffer();
    int ii=0;
    for (Method method : module.getMethodList()) {
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
      for (int k = 0; k < eg.length; k++) {
        Annotation[] egx = eg[k];
        for (Annotation egz : egx) {
          if (egz instanceof Eg) {
            egs[k] = ((Eg) egz).value();
          }
        }
      }
      Class<?>[] paramTypes = method.getParameterTypes();
      String iii = Integer.toString(ii);
      for (int j = 0; j < paramTypes.length; j++) {
        Class<?> paramType = paramTypes[j];
        // The special type that we handle transparently
        String value = "";
        if (egs[j] != null) value = "\"" + Json.escapeJavaScript(egs[j]) + "\"";
        else {
          value = paramType == String.class ? "\"\""
              : paramType == Boolean.class || paramType == Boolean.TYPE ? "true" : paramType == Integer.class
                  || paramType == Integer.TYPE || paramType == Short.class || paramType == Short.TYPE || paramType == Long.class
                  || paramType == Long.TYPE || paramType == Byte.class || paramType == Byte.TYPE ? "0" : paramType == Float.class
                  || paramType == Float.TYPE || paramType == Double.class || paramType == Double.TYPE ? "0.0" : paramType.isArray()
                  || Collection.class.isAssignableFrom(paramType) ? "[]" : Map.class.isAssignableFrom(paramType) ? "{}" : "";
        }
        int sz = 20;
        if (value.length() > sz) sz = value.length() + 5;
        String input = "    <input class='itext' type='text' size='" + sz + "' value='" + value + "' id='p" + iii + "_" + j
            + "' title='Will be converted to: " + paramType.getName() + "'/>";
        if (paramType == BufferedImage.class || paramType == FileTransfer.class) {
          input = "    <input class='itext' type='file' id='p" + iii + "_" + j + "'/>";
        }
        sb.append(input);
        sb.append(j == paramTypes.length - 1 ? "" : ", \n");
      }
      sb.append("  );\n");
      sb.append("<input class='ibutton' type='button' onclick='");
      sb.append("doClick(\"").append(scriptName).append("\",\"").append(methodName).append("\",").append(iii)
          .append(",").append(Integer.toString(paramTypes.length)).append(")'");
      sb.append(" value='Execute' title='Calls ").append(scriptName).append(".").append(methodName)
          .append("().' /><div class=\"output\" id='d").append(iii).append("' class='reply'></div>")
          .append("</li>\n");
      ii++;
    }
    pg = rp(pg, "methods", sb.toString());
    return pg;
  }
  public void handle(HttpServletRequest request, HttpServletResponse response) throws BrijjException, IOException {
    Object[] ov = parsePost(request);
    Object rsp = invoker( request.getPathInfo().substring("/call/".length()), ov, request, response);
    writeJavascript(response, rsp);
  }
  
  private Object[] parsePost(HttpServletRequest req) throws BrijjException {
    List<Object> lf = new LinkedList<Object>();
    if (isMultipartContent(req)) {
      try {
        Collection<Part> p = req.getParts();
        /*for (int i = 0; i < p.size(); i++)
          lf.add(null);
          */
        for (Part z : p) {
          String n = z.getName();
          Object o = readObject(z);
          // lf.set(Integer.valueOf(z.getName().substring(1)), readObject(z));
          lf.add(o);
        }
        return lf.toArray();
      } catch (ServletException sx) {
        throw new BrijjException(sx);
      } catch (IOException ix) {
        throw new BrijjException(ix);
      }
    } else {
      BufferedReader in = null;
      try {
        String ce = req.getCharacterEncoding();
        InputStream is = req.getInputStream();
        InputStreamReader isr = ce != null ? new InputStreamReader(is, ce) : new InputStreamReader(is);
        in = new BufferedReader(isr);
        while (true) {
          String line = in.readLine();
          if (line == null) return lf.toArray();
          if (line.indexOf('&') == -1) lf.add(readObject(line));
          // If there are any &'s then this must be iframe post
          else {
            StringTokenizer st = new StringTokenizer(line, "&");
            while (st.hasMoreTokens())
              lf.add(readObject(urlDecode(st.nextToken())));
          }
        }
      } catch (Exception ex) {
        throw new BrijjException("Failed to read input", ex);
      } finally {
        if (in != null) try {
          in.close();
        } catch (IOException ex) {}
      }
    }
  }
  private static boolean isMultipartContent(HttpServletRequest request) {
    if (!"post".equals(request.getMethod().toLowerCase())) { return false; }
    String contentType = request.getContentType();
    if (contentType == null) { return false; }
    if (contentType.toLowerCase().startsWith("multipart/")) { return true; }
    return false;
  }
  private Object[] findMethod(Object[] ov, String scriptName, String methodName) throws ClassNotFoundException {
    int inputArgCount = ov.length;
    // Get a mutable list of all methods on the type specified by the creator
    RemoteRequestProxy module = RemoteRequestProxy.getModule(scriptName, null, null);
    List<Method> allMethods = new ArrayList<Method>();
    List<Object[]> ovl = new ArrayList<Object[]>();
    
    for (Method m : module.getMethodList()) { // only use methods with matching
      if (m.getName().equals(methodName)) allMethods.add(m);
    }
    if (allMethods.isEmpty()) {
      // Not even a name match
      throw new IllegalArgumentException("Method name not found: " + methodName);
    }
    // Remove all the methods where we can't convert the parameters
    List<Method> am = new ArrayList<Method>();
    Object[] nov = null;
    allMethodsLoop: for (Method m : allMethods) {
      Class<?>[] methodParamTypes = m.getParameterTypes();
      if (inputArgCount == 0 && methodParamTypes.length == 0) {
        am.add(m);
        ovl.add(nov);
        continue;
      }
      // Remove non-varargs methods which declare less params than were passed
      if (!m.isVarArgs() && methodParamTypes.length < inputArgCount) continue allMethodsLoop;
      if (m.isVarArgs()) {
        int z = methodParamTypes.length - 1;
        if (inputArgCount < z) continue allMethodsLoop;
        nov = new Object[z+1];
        int pc = ov.length - z;
        Object[] va = new Object[pc];
        for(int i=0;i<z;i++) {
          nov[i]=ov[i];
        }
        for (int i = 0; i < pc; i++) {
          va[i] = ov[z + i];
        }
        // BUG: ov needs to be shortened to length z+1
        // This modified "ov" however, is associated with this method -- and the previos ov is associated with its method
        nov[z] = va;
      } else {
        if (methodParamTypes.length != inputArgCount) continue allMethodsLoop;
        nov = new Object[ov.length];
        for(int i=0;i<ov.length;i++) nov[i]=ov[i];
      }
      // Remove methods where we can't convert the input
      for (int i = 0; i < methodParamTypes.length; i++) {
        Class<?> methodParamType = methodParamTypes[i];
        Object param = nov[i];
        if (param != null && param.getClass() == FileTransfer.class) {
          param = ((FileTransfer) param).asObject();
          nov[i] = param;
        }
        if (inputArgCount <= i && methodParamType.isPrimitive()) continue allMethodsLoop;
        boolean ok = false;
        try {
          Object zpar = Cast.cast(methodParamType, param);
          nov[i] = zpar;
          ok = true;
        } catch (CastException cx) {
          ok = false;
        }
        if (!ok) continue allMethodsLoop;
      }
      am.add(m);
      ovl.add(nov);
    }
    if (am.isEmpty()) {
      // Not even a name match
      throw new IllegalArgumentException("Method not found: "+methodName);
    } else if (am.size() == 1) { return new Object[]{ am.get(0), ovl.get(0) }; }
    else { return new Object[] { am.get(0), ovl.get(0) }; }
    // throw new IllegalArgumentException("Multiple methods found -- the method mapping is ambiguous");
  }
  public void writeJavascript(HttpServletResponse response, Object obj) throws IOException {
    if (obj instanceof Throwable) {
      writeThrowable(response, (Throwable) obj);
      return;
    }
    PrintWriter p = response.getWriter();
    response.setContentType("text/brijj"); // "text/javascript; charset=\"utf=8\"");
    p.write("c:");
    Json.writeObject(obj, p);
  }
  private void writeThrowable(HttpServletResponse response, Throwable tt) throws IOException {
    PrintWriter p = response.getWriter();
    Throwable t = tt;
    while (t instanceof InvocationTargetException)
      t = ((InvocationTargetException) t).getTargetException();
    response.setContentType("text/brijj"); // "text/javascript; charset=\"utf=8\"");
    p.write("x:({javaClassName:\"");
    p.write(t.getClass().getName());
    p.write("\",message:\"");
    p.write(Json.escapeJavaScript(t.getMessage()));
    p.write("\"})");
  }
  private static String getFilename(Part part) {
    for (String cd : part.getHeader("content-disposition").split(";")) {
        if (cd.trim().startsWith("filename=")) {
            return cd.substring(cd.indexOf('=') + 1).trim().replace("\"", "");
        }
    }
    return null;
}
  private static Object readObject(Part z) {
    try {
      String ct = z.getContentType();
      if (ct == null) return readObject(readAllTextFrom(new InputStreamReader(z.getInputStream(),"UTF-8")));
      // TODO: Figure out how to handle iamges properly?
      // else if (ct.startsWith("image/")) return ImageIO.read(z.getInputStream());
      else return new FileTransfer(getFilename(z), ct, readAllBytesFrom(z.getInputStream()), null);
      // else if (ct.startsWith("text/")) return readAllTextFrom(new InputStreamReader(z.getInputStream(), "UTF-8"));
      // else return readAllBytesFrom(z.getInputStream());
    } catch (IOException ex) {
      return null;
    }
  }
  private static Object readObject(String vv) {
    char t = vv.charAt(0);
    String v = vv.substring(2);
    switch (t) {
    case 'z':
      return null;
    case 's':
      return urlDecode(v);
    case 'n':
      try {
        if (v.indexOf(".") != -1) return Double.parseDouble(v);
        else return Integer.parseInt(v);
      } catch (NumberFormatException nfe) {}
      try {
        return Long.parseLong(v);
      } catch (NumberFormatException nfe) {}
      try {
        return Double.parseDouble(v);
      } catch (NumberFormatException nfe) {}
      throw new RuntimeException("failed to parse numeric: " + v);
    case 'b':
      return Boolean.parseBoolean(v);
    case 'a': {
      v = v.substring(1, v.length() - 1);
      LinkedList<Object> ll = new LinkedList<Object>();
      StringTokenizer st = new StringTokenizer(v, ",");
      while (st.hasMoreElements())
        ll.add(readObject(
            urlDecode( st.nextToken() ) ));
      Cast ca = new Cast();
      if (ll.size() == 0) return ca.castToArray(Object.class,  ll);
      Set<Class<?>> classes = new HashSet<Class<?>>();
      for(Object o : ll) classes.add(o.getClass());
      Class<?>[]tc = Cast.commonSuperClasses(classes); 
      Class<?> nc = tc.length == 0 ? Object.class : tc[0];
      if (nc.equals(Integer.class)) nc = Integer.TYPE;
      else if (nc.equals(Double.class)) nc = Double.TYPE;
      return ca.castToArray(nc,  ll);
    }
    case 'o': {
      HashMap<String, Object> hm = new HashMap<String, Object>();
      StringTokenizer st = new StringTokenizer(v.substring(1, v.length() - 1), ",");
      while (st.hasMoreElements()) {
        String tkn = st.nextToken(); 
        // needed sometime, not others?
        tkn = urlDecode(tkn);
        int n = tkn.indexOf(':');
        hm.put(tkn.substring(0, n).trim(), readObject(tkn.substring(n + 1)));
      }
      return hm;
    }
    case 'd': {
        return new Date( Long.parseLong(v));
    }
    default:
      throw new RuntimeException("unknown inbound parameter type: " + t);
    }
  }
  static String urlDecode(String value) {
    try {
      return URLDecoder.decode(value, "UTF-8");
    } catch (UnsupportedEncodingException ignore) {
      return value;
    }
  }
  static class BrijjException extends Exception {
    private static final long serialVersionUID = -707126214405629313L;
    public BrijjException(String message, Throwable ex) {
      super(message, ex);
    }
    public BrijjException(Throwable ex) {
      super(ex.getMessage(), ex);
    }
  }
  public static String readAllTextFrom(URL url) throws IOException {
    return readAllTextFrom(new InputStreamReader(url.openConnection().getInputStream(), "UTF-8"));
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
