package net.r0kit.brijj;

import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public abstract class RemoteRequestProxy {
  protected HttpServletRequest request;
  protected HttpServletResponse response;
  protected Locale locale;
  protected HttpSession sess;
  protected ServletContext context;
  protected static Map<String, Class<?>> proxies = new HashMap<String, Class<?>>();

  public static Class<?> register(Class<?> cl) {
    if (RemoteRequestProxy.class.isAssignableFrom(cl) || Remotable.class.isAssignableFrom(cl)
        ) proxies.put(className(cl), cl);
    else throw new RuntimeException("cannot register " + cl);
    return cl;
  }
  public static Set<String> getCreatorNames() {
    HashSet<String> hs = new HashSet<String>();
    for (String s : proxies.keySet()) {
      if (RemoteRequestProxy.class.isAssignableFrom(proxies.get(s))) hs.add(s);
    }
    return hs;
  }
  private static Set<Class<?>> dataClasses() {
    HashSet<Class<?>> tc = new HashSet<Class<?>>();
    for (Map.Entry<String, Class<?>> e : proxies.entrySet()) {
      if (e.getValue().isInstance(Remotable.class)) tc.add(e.getValue());
    }
    return tc;
  }
  public Method[] getMethods() {
    Method[] methods = getClass().getMethods();
    ArrayList<Method> methodDecls = new ArrayList<Method>();
    for (Method method : methods) {
      if (null != method.getAnnotation(Brijj.RemoteMethod.class)) {
        methodDecls.add(method);
      }
    }
    return methodDecls.toArray(new Method[0]);
  }
  public static RemoteRequestProxy getModule(String scriptName, HttpServletRequest req, HttpServletResponse rsp)
      throws ClassNotFoundException {
    Class<?> proxy = proxies.get(scriptName);
    if (proxy == null) {
      try {
        Class<?> z = RemoteRequestProxy.class.getClassLoader().loadClass(scriptName);
        // TODO: perhaps guess at some good package names to try?
        proxy = proxies.get(scriptName);
        if (proxy == null && RemoteRequestProxy.class.isAssignableFrom(z)) proxy = register(z);
      } catch (ClassNotFoundException ignore) {}
    }
    if (proxy == null) throw new ClassNotFoundException(scriptName);
    try {
      return getInstance(proxy, req, rsp);
    } catch (InstantiationException rethrow) {
      throw new ClassNotFoundException(scriptName);
    }
  }
  public static Class<?> classFor(String s) throws ClassNotFoundException {
    Class<?> cc = Class.forName(s);
    if (Remotable.class.isAssignableFrom(cc)) return cc;
    throw new ClassNotFoundException(s);
  }
  public static Class<?> getData(String scriptName) throws ClassNotFoundException {
    Class<?> proxy = proxies.get(scriptName);
    if (proxy == null) {
      try {
        Class<?> z = Class.forName(scriptName);
        // TODO: perhaps guess at some good package names to try?
        proxy = proxies.get(scriptName);
        if (proxy != null && Remotable.class.isAssignableFrom(proxy)) proxy = register(z);
      } catch (ClassNotFoundException ignore) {}
    }
    if (proxy == null) throw new ClassNotFoundException(scriptName);
    return proxy;
  }
  private static RemoteRequestProxy getInstance(Class<?> clazz, HttpServletRequest request, HttpServletResponse response)
      throws InstantiationException {
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
    } catch (IllegalAccessException ex) {
      throw new InstantiationException("Illegal Access to default constructor on " + clazz.getName());
    } catch (NoSuchMethodException nsme) {
      throw new InstantiationException("Unknown default constructor on " + clazz.getName());
    } catch (InvocationTargetException x) {
      throw new InstantiationException(x.getTargetException().toString());
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
    Brijj.RemoteAttributes createAnn = cl.getAnnotation(Brijj.RemoteAttributes.class);
    String scriptName = createAnn == null || createAnn.name() == null || createAnn.name().isEmpty() ? cl.getSimpleName()
      : createAnn.name();
    return scriptName;
  }

  public static long lastModifiedTime = System.currentTimeMillis();

  public Map<String, Object> getPropertyMapFromClass(Class<?> cc) {
    Field[] fs = cc.getFields();
    Map<String, Object> res = new HashMap<String, Object>();
    for (Field f : fs) {
      res.put(f.getName(), f);
    }
    return res;
  }
  protected String generateDtoJavaScript(Class<?> cc) {
    // if (cc == null) return "throw new Error('class not found: " + jsClassName
    // + "');";
    StringBuilder buf = new StringBuilder();
    // Generate (1): <assignVariable> = function() {
    buf.append("c = function() {\n");
    // Generate (2): <indent> this.myProp = <initial value>;
    Map<String, Object> properties = getPropertyMapFromClass(cc);
    for (Entry<String, Object> entry : properties.entrySet()) {
      String name = entry.getKey();
      Object property = entry.getValue();
      Class<?> propType = property instanceof Field ? ((Field) property).getType() : ((PropertyDescriptor) property)
          .getPropertyType();
      // Property name
      buf.append("  this.");
      buf.append(name);
      buf.append(" = ");
      // Default property values
      buf.append(propType.isArray() ? "[]" : propType == boolean.class ? "false" : propType.isPrimitive() ? "0" : "null");
      buf.append(";\n");
    }
    buf.append("}\n");
    buf.append("c.$brijjClassName = '");
    buf.append(className());
    buf.append("';\n");
    buf.append("c.$brijjClassMembers = {};\n");
    for (String name : properties.keySet()) {
      buf.append("c.$brijjClassMembers.");
      buf.append(name);
      buf.append(" = {};\n");
    }
    // Generate (6): <assignVariable>.createFromMap =
    // brijj.engine._createFromMap;
    buf.append("c.createFromMap = brijj.engine._createFromMap;\n");
    return buf.toString();
  }
  protected String generateDtoInheritanceJavaScript(String classExpression, String superClassExpression, String delegateFunction) {
    // The desired output is something like this (not wrapped by any
    // "if already defined clauses" as this is not needed on all module
    // systems):
    // <indent><classExpression>.prototype =
    // <delegateFunction>(<superClassExpression>.prototype);
    // <indent><classExpression>.prototype.constructor = <classExpression>;
    StringBuilder buf = new StringBuilder();
    buf.append(classExpression);
    buf.append(".prototype = " + delegateFunction + "(");
    buf.append(superClassExpression);
    buf.append(".prototype);\n");
    buf.append(classExpression);
    buf.append(".prototype.constructor = ");
    buf.append(classExpression);
    buf.append(";\n");
    return buf.toString();
  }
  public String generateDtoAllScript() {
    StringBuilder sb = new StringBuilder("(function() { var c; var addedNow = [];\n");
    // DTO class definitions
    Set<Class<?>> ss = RemoteRequestProxy.dataClasses();
    for (Class<?> jsClass : ss) {
      String cnam = className(jsClass);
      sb.append("\n  if (!brijj.engine._mappedClasses[\"" + cnam + "\"]) {\n")
          .append(generateDtoJavaScript(jsClass))
          .append(
              "    brijj.engine._setObject(\"${cl}\", c);    brijj.engine._mappedClasses[\"${cl}\"] = c; addedNow[\"${cl}\"] = true;  }\n"
                  .replace("${cl}", cnam));
    }
    sb.append("})();\n");
    return sb.toString();
  }
  public String generateInterfaceScript(String csp, String scriptName) {
    StringBuilder buffer = new StringBuilder();
    buffer
        .append("if (typeof brijj == 'undefined' || brijj.engine == undefined) throw new Error('You must include BriJJ engine before including this file');\n");
    // An optimization here might be to only generate class
    // definitions for classes used as parameters in the class that we are
    // currently generating a proxy for.
    buffer.append(generateDtoAllScript());
    buffer.append("\n(function() {  if (brijj.engine._getObject(\"" + scriptName + "\") == undefined) {\n    var p;")
        .append(generateInterfaceJavaScript(scriptName, csp))
        .append("    brijj.engine._setObject(\"" + scriptName + "\", p); } })();\n");
    return buffer.toString();
  }
  protected String generateTemplate(String contextPath, String servletPath, String pathInfo) throws IOException {
    String scriptName = pathInfo;
    String js = ".js";
    String ihu = "/interface/";
    if (!scriptName.startsWith(ihu) || !scriptName.endsWith(js)) { return null; }
    scriptName = scriptName.substring(ihu.length());
    scriptName = scriptName.substring(0, scriptName.length() - js.length());
    // FIXME: Figure out whether I'm requesting a remote proxy or a DTO
    // then do the right thing
    try {
      RemoteRequestProxy rp = RemoteRequestProxy.getModule(scriptName, null, null);
      scriptName = rp.className();
    } catch (ClassNotFoundException remap) {
      throw new IOException(remap.toString());
    }
    return generateInterfaceScript(contextPath + servletPath, scriptName);
  }
  @Override public String toString() {
    return this.getClass().getSimpleName() + "( ... )";
  }
  private String generateInterfaceJavaScript(String scriptName, String contextServletPath) {
    StringBuilder buffer = new StringBuilder("p = {}; p._path = '" + contextServletPath + "';\n");
    RemoteRequestProxy module;
    try {
      module = RemoteRequestProxy.getModule(scriptName, null, null);
    } catch (ClassNotFoundException ignore) {
      System.err.println(ignore.toString());
      return buffer.toString();
    }
    Method[] methods = module.getMethods();
    Arrays.sort(methods, new Comparator<Method>() {
      public int compare(Method a, Method b) {
        return a.getName().compareTo(b.getName());
      }
    });
    for (Method method : methods) {
      String methodName = method.getName();
      // Is it on the list of banned names
      if (JavascriptOutput.isReserved(methodName)) continue;
      Class<?>[] paramTypes = method.getParameterTypes();
      // Create the function definition
      buffer.append("p." + methodName + " = function(");
      for (int j = 0; j < paramTypes.length; j++) {
        buffer.append("p").append(j).append(", ");
      }
      buffer.append("callback) {  return brijj.engine._execute(p._path, '");
      buffer.append(scriptName);
      buffer.append("', '");
      buffer.append(methodName);
      buffer.append("\', arguments); };\n");
    }
    return buffer.toString();
  }
}
