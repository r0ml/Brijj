package net.r0kit.brijj;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import net.r0kit.brijj.Brijj.Remotable;

public abstract class RemoteRequestProxy {
  protected HttpServletRequest request;
  protected HttpServletResponse response;
  protected Locale locale;
  protected HttpSession sess;
  protected ServletContext context;
  protected static Map<String, Class<? extends RemoteRequestProxy>> proxies = new HashMap<String, Class<? extends RemoteRequestProxy>>();

  public static void register(Class<? extends RemoteRequestProxy>... cls) {
    for(Class<? extends RemoteRequestProxy> cl : cls) {
    if (RemoteRequestProxy.class.isAssignableFrom(cl) || Remotable.class.isAssignableFrom(cl)
        ) proxies.put(className(cl), cl);
    else throw new RuntimeException("cannot register " + cl);
    }
  }
  public static Collection<Class<? extends RemoteRequestProxy>> getProxies() {
    return proxies.values();
  }
  public static Set<String> getProxyNames() {
    Set<String> hs = new TreeSet<String>();
    for (String s : proxies.keySet()) {
      if (RemoteRequestProxy.class.isAssignableFrom(proxies.get(s))) hs.add(s);
    }
    return hs;
  }
  
  public Method[] getMethods() {
    Method[] methods = getClass().getMethods();
    ArrayList<Method> methodDecls = new ArrayList<Method>();
    for (Method method : methods) {
      if (null != method.getAnnotation(Brijj.RemoteMethod.class)) methodDecls.add(method);
    }
    return methodDecls.toArray(new Method[0]);
  }
  
  public static RemoteRequestProxy getModule(String scriptName, HttpServletRequest request, HttpServletResponse response)
      throws ClassNotFoundException {
    Class<? extends RemoteRequestProxy> clazz = proxies.get(scriptName);
    if (clazz == null) throw new ClassNotFoundException(scriptName);
     Locale loca = new Locale("en");
    if (request != null) {
      String lang = request.getHeader("accept-language");
      if (lang != null) loca = new Locale(lang.split(",")[0]);
      if (null != request.getServletContext().getEffectiveSessionTrackingModes()) {
        request.getSession().setAttribute("locale", loca);
      }
    }
    try {
      return (RemoteRequestProxy) clazz.getConstructor(HttpServletRequest.class, HttpServletResponse.class).newInstance(request,
          response);
    } catch(InstantiationException rethrow) {
      throw new ClassNotFoundException(scriptName+": "+rethrow.toString());
    } catch (IllegalAccessException ex) {
      throw new ClassNotFoundException("Illegal Access to default constructor on " + clazz.getName());
    } catch (NoSuchMethodException nsme) {
      throw new ClassNotFoundException("Unknown default constructor on " + clazz.getName());
    } catch (InvocationTargetException x) {
      throw new ClassNotFoundException(x.getTargetException().toString());
    }
 }
  
  public RemoteRequestProxy(HttpServletRequest r, HttpServletResponse s) {
    locale = new Locale("en");
    if (r != null) {
      context = r.getServletContext();
      sess = r.getServletContext().getEffectiveSessionTrackingModes() == null ? null : r.getSession();
      request = r;
      String lang = request.getHeader("accept-language");
      if (lang != null) locale = new Locale(lang.split(",")[0]);
    }
    response = s;
  }
  public String className() {
    return className(getClass());
  }
  // FIXME: possibly prepend the browser context for this remote class?
  private static String className(Class<?> cl) {
    Brijj.RemoteAttribute createAnn = cl.getAnnotation(Brijj.RemoteAttribute.class);
    String scriptName = createAnn == null || createAnn.name() == null || createAnn.name().isEmpty() ? cl.getSimpleName()
      : createAnn.name();
    return scriptName;
  }

  @Override public String toString() {
    return this.getClass().getSimpleName() + "( ... )";
  }

  public String generateInterfaceScript(String csp, String scriptName) {
    RemoteRequestProxy module;
    try {
      module = RemoteRequestProxy.getModule(scriptName, null, null);
    } catch (ClassNotFoundException ignore) {
      System.err.println(ignore.toString());
      return "";
    }

    StringBuilder buffer = new StringBuilder();

    // defines the java classes in the global context
    buffer.append("\n(function() {  var _ = window; if (_." + scriptName + " == undefined) {\n    var p;");
    buffer.append("p = {}; p._path = '" + csp + "';\n");
    Method[] methods = module.getMethods();
    Arrays.sort(methods, new Comparator<Method>() {
      public int compare(Method a, Method b) {
        return a.getName().compareTo(b.getName());
      }
    });
    for (Method method : methods) {
      String methodName = method.getName();
      // Is it on the list of banned names
      if (Json.isReserved(methodName)) continue;
      Class<?>[] paramTypes = method.getParameterTypes();
      // Create the function definition
      buffer.append("p." + methodName + " = function(");
      for (int j = 0; j < paramTypes.length; j++) buffer.append("p").append(j).append(", ");
      buffer.append("callback) {  return brijj._execute(p._path, '");
      buffer.append(scriptName);
      buffer.append("', '");
      buffer.append(methodName);
      buffer.append("\', arguments); };\n");
    }
    buffer.append("    _." + scriptName + "=p; } })();\n");
    return buffer.toString();
  }
}
