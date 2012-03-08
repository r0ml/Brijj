package net.r0kit.brijj;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import net.r0kit.brijj.Cast.CastException;

public class CallHandler {
  static class BrijjException extends Exception {
    private static final long serialVersionUID = -707126214405629313L;
    public BrijjException(String message, Throwable ex) {
      super(message, ex);
    }
    public BrijjException(Throwable ex) {
      super(ex.getMessage(), ex);
    }
  }
  public void handle(HttpServletRequest request, HttpServletResponse response) throws BrijjException, IOException {
    Object[] ov = setUpCall(request);
    Object rsp = new Throwable("rsp not initialized");
    try {
      String smn = request.getPathInfo().substring("/call/".length());
      String[] smns = smn.split("\\.");
      String clazz = smns[0];
      Method method = findMethod(ov, clazz, smns[1]);
      if (method == null) { throw new IllegalArgumentException("Missing method or missing parameter converters"); }
      // Convert all the parameters to the correct types
      int destParamCount = method.getParameterTypes().length;
      Object[] arguments = new Object[destParamCount];
      for (int j = 0; j < destParamCount; j++) {
        Object param = ov[j];
        Type paramType = method.getGenericParameterTypes()[j];
        arguments[j] = Cast.cast(paramType, param);
      }
      RemoteRequestProxy object = RemoteRequestProxy.getModule(clazz, request, response);
      Object res = method.invoke(object, arguments);
      rsp = res;
    } catch (Throwable ex) {
      rsp = ex;
    }
    if (rsp instanceof BufferedImage) {
      rsp = new FileTransfer((BufferedImage)rsp ,"png");
    } 
    writeJavascript(response, rsp);
  }
  private Object[] setUpCall(HttpServletRequest request) throws BrijjException {
    List<Object> lf = new LinkedList<Object>();
    
    try {
      parsePost(request, lf);
    } catch (IOException iox) {
      throw new BrijjException(iox);
    } catch (ServletException sox) {
      throw new BrijjException(sox);
    }
    return lf.toArray();
/*    try { return parseParameters(request, lf); }
    catch(ClassNotFoundException rethrow) { throw new BrijjException("parsing parameters", rethrow); }
    catch(IOException rethrow) { throw new BrijjException("parsing parameters", rethrow); }
    */ 
  }

  private void parsePost(HttpServletRequest req, List<Object> lf) throws BrijjException, ServletException, IOException {
    if (isMultipartContent(req)) {
      Collection<Part> p = req.getParts();
      for(int i=0;i<p.size();i++) lf.add(null); 
      for (Part z : p) lf.set( Integer.valueOf(z.getName().substring(1))  , readObject(z));
    } else {
      BufferedReader in = null;
      try {
        String ce = req.getCharacterEncoding();
        InputStream is = req.getInputStream();
        InputStreamReader isr = ce != null ? new InputStreamReader(is, ce) : new InputStreamReader(is);
        in = new BufferedReader(isr);
        while (true) {
          String line = in.readLine();
          if (line == null) break;
          if (line.indexOf('&') == -1) lf.add( readObject(line) );
          // If there are any &'s then this must be iframe post
          else {
            StringTokenizer st = new StringTokenizer(line, "&");
            while (st.hasMoreTokens()) {
              lf.add( readObject( urlDecode(st.nextToken())));
            }
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
  private Method findMethod(Object[] ov, String scriptName, String methodName) throws ClassNotFoundException {
    int inputArgCount = ov.length;
    // Get a mutable list of all methods on the type specified by the creator
    RemoteRequestProxy module = RemoteRequestProxy.getModule(scriptName, null, null);
    List<Method> allMethods = new ArrayList<Method>();
    for (Method m : module.getMethods()) { // only use methods with matching
                                           // name
      if (m.getName().equals(methodName)) allMethods.add(m);
    }
    if (allMethods.isEmpty()) {
      // Not even a name match
      throw new IllegalArgumentException("Method name not found: " + methodName);
    }
    // Remove all the methods where we can't convert the parameters
    List<Method> am = new ArrayList<Method>();
    allMethodsLoop: for (Method m : allMethods) {
      Class<?>[] methodParamTypes = m.getParameterTypes();
      if (inputArgCount == 0 && methodParamTypes.length == 0) {
        am.add(m);
        continue;
      }
      // Remove non-varargs methods which declare less params than were passed
      if (!m.isVarArgs() && methodParamTypes.length < inputArgCount) continue allMethodsLoop;
      if (m.isVarArgs()) {
        int z = methodParamTypes.length - 1;
        if (inputArgCount < z) continue allMethodsLoop;
        int pc = ov.length;
        Object[] va = new Object[pc - z];
        for (int i = 0; i < pc; i++) {
          va[i] = ov[z + i];
        }
        ov[z]=va;
      } else if (methodParamTypes.length != inputArgCount) continue allMethodsLoop;
      // Remove methods where we can't convert the input
      for (int i = 0; i < methodParamTypes.length; i++) {
        Class<?> methodParamType = methodParamTypes[i];
        Object param = ov[i];
        if (param != null && param.getClass() == FileTransfer.class) {
          param = ((FileTransfer) param).asObject();
          ov[i]=param;
        }
        if (inputArgCount <= i && methodParamType.isPrimitive()) continue allMethodsLoop;
        boolean ok = false;
        try {
          Object zpar = Cast.cast(methodParamType, param);
          ov[i]=zpar;
          ok = true;
        } catch (CastException cx) {
          ok = false;
        }
        if (!ok) continue allMethodsLoop;
      }
      am.add(m);
    }
    if (am.isEmpty()) {
      // Not even a name match
      throw new IllegalArgumentException("Method not found. See logs for details");
    } else if (am.size() == 1) { return am.get(0); }
    throw new IllegalArgumentException("Multiple methods found -- the method mapping is ambiguous");
  }
  

  public void writeJavascript(HttpServletResponse response, Object obj) throws IOException {
    if (obj instanceof Throwable) {
      writeThrowable(response, (Throwable) obj);
      return;
    }
    PrintWriter p = response.getWriter();
    response.setContentType("text/html"); // "text/javascript; charset=\"utf=8\"");
    p.write("c:");
    Json.writeObject(obj, p);
  }
  
  private void writeThrowable(HttpServletResponse response, Throwable tt) throws IOException {
    PrintWriter p = response.getWriter();
    Throwable t = tt;
    while (t instanceof InvocationTargetException) t = ((InvocationTargetException) t).getTargetException();
    response.setContentType("text/html"); // "text/javascript; charset=\"utf=8\"");
    p.write("x:({javaClassName:\"");
    p.write(t.getClass().getName());
    p.write("\",message:\"");
    p.write(Json.escapeJavaScript(t.getMessage()));
    p.write("\"})");
  }

  private static Object readObject(Part z) {
//      this.name = z.getName();
//      this.mimeType = z.getContentType();
//      this.fileSize = z.getSize();
//      isFile = z.getHeader("content-disposition").contains("filename=");
//      try { this.inputStream = z.getInputStream(); }
//      catch(IOException iox) {}
//    }

//      if (hasBeenRead == true) {
//        throw new RuntimeException("reading a FormField twice should never happen -- if it does, the inputStream needs to be reset");

    //if (mimeType.startsWith("text/")) return asString();

    try {
      if (z.getContentType().startsWith("image/")) return ImageIO.read(z.getInputStream());
      else if (z.getContentType().startsWith("text/")) return Brijj.readAllTextFrom(new InputStreamReader(z.getInputStream(),"UTF-8"));
      else return Brijj.readAllBytesFrom(z.getInputStream());
    } catch (IOException ex) {
       return null;
    }
  }
  
  
  private static Object readObject(String vv) {
      char t = vv.charAt(0);
      String v = vv.substring(2);
      switch(t) {
      case 'z': return null;
      case 's': return urlDecode(v);
      case 'n':
        try { return v.indexOf(".") != -1 ? Double.parseDouble(v) : Integer.parseInt(v); }
        catch (NumberFormatException nfe) {}
        try { return Long.parseLong(v); } catch (NumberFormatException nfe) {}
        try { return Double.parseDouble(v); } catch (NumberFormatException nfe) {}
        throw new RuntimeException("failed to parse numeric: "+ v);
      case 'b': return Boolean.parseBoolean(v);
      case 'a': {
        v = v.substring(1, v.length()-1);
        LinkedList<Object> ll = new LinkedList<Object>();
        StringTokenizer st = new StringTokenizer(v, ",");
        while (st.hasMoreElements()) ll.add(readObject(urlDecode(st.nextToken())));
        Cast ca = new Cast();
        if(ll.get(0).getClass().equals(Integer.class)) 
          return ca.castToArray(Integer.TYPE, ll);
        else if(ll.get(0).getClass().equals(Double.class))
          return ca.castToArray(Double.TYPE, ll);
        else throw new RuntimeException("failed to parse array");
      }
      case 'o': {
        HashMap<String, Object> hm = new HashMap<String, Object>();
        StringTokenizer st = new StringTokenizer(v.substring(1, v.length() - 1), ",");
        while (st.hasMoreElements()) {
          KeyValue tv = splitInbound(urlDecode(st.nextToken()));
          hm.put(tv.key, readObject(tv.value));
        }
        return hm;
      }
      default: throw new RuntimeException("unknown inbound parameter type: "+t);
      }
    }

    static class KeyValue {
      public String key, value;
      public KeyValue(String t, String v) {
        key = t;
        value = v;
      }
    }

    static KeyValue splitInbound(String data) {
      int n = data.indexOf(':');
      return new KeyValue(data.substring(0,n).trim(), data.substring(n+1));
    }
 /*   Object[] parseParameters(HttpServletRequest request, List<FormField> extraParameters) throws ClassNotFoundException, IOException {
      List<Object> inboundContext = new ArrayList<Object>();
      for (FormField formField : extraParameters) {
        inboundContext.add(formField.isFile ? new FileTransfer(formField, request) : readObject(formField.getString()));
      }
      return inboundContext.toArray();
    } 
   */
    
    static String urlDecode(String value) {
      try { return URLDecoder.decode(value, "UTF-8"); }
      catch (UnsupportedEncodingException ignore) { return value; }
    }
}
