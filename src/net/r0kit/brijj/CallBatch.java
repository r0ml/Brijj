package net.r0kit.brijj;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;


class CallBatch {
  public final Map<String, FormField> extraParameters;
  Outbound ov;
  // InboundContext inboundContext;
  protected HttpServletRequest request;
  public String batchId, scriptName, methodName;
  Method method = null;
  Object[] parameters = null;
  Throwable exception = null;
  public Object reply;

  public CallBatch(HttpServletRequest q) throws ServerException {
    request = q;
    try { extraParameters = parsePost(request); }
    catch(IOException iox) { throw new ServerException(iox); }
    catch(ServletException sox) { throw new ServerException(sox); }
    parseParameters();
  }
  private String extractParameter(String paramName) {
    FormField formField = extraParameters.remove(paramName);
    if (formField != null) { return formField.getString(); }
    throw new IllegalArgumentException("Failed to find parameter: " + paramName);
  }
  private Map<String, FormField> parseBasicPost(HttpServletRequest req) throws ServerException {
    Map<String, FormField> paramMap;
    paramMap = new HashMap<String, FormField>();
    BufferedReader in = null;
    try {
      String charEncoding = req.getCharacterEncoding();
      if (charEncoding != null) {
        in = new BufferedReader(new InputStreamReader(req.getInputStream(), charEncoding));
      } else {
        in = new BufferedReader(new InputStreamReader(req.getInputStream()));
      }
      while (true) {
        String line = in.readLine();
        if (line == null) break;
        if (line.indexOf('&') == -1) parsePostLine(line, paramMap); 
          // If there are any &'s then this must be iframe post 
        else {
          StringTokenizer st = new StringTokenizer(line, "&");
          while (st.hasMoreTokens()) {
            String part = st.nextToken();
            part = JavascriptInput.urlDecode(part);
            parsePostLine(part, paramMap);
          }
        }
      }
    } catch (Exception ex) {
      throw new ServerException("Failed to read input", ex);
    } finally {
      if (in != null) try { in.close(); } catch (IOException ex) {}
    }
    return paramMap;
  }
  private void parseParameters() {
    batchId = extractParameter("batchId");
    scriptName = extractParameter( "scriptName");
    methodName = extractParameter( "methodName");
     
    
    ov = new Outbound(null);
    try { 
      ov.readExternal( new JavascriptInput(extraParameters, request));
    } catch(IOException ioe) {
      System.err.println(ioe);
    } catch(ClassNotFoundException cnf) {
      System.err.println(cnf);
    }
  }
  private Map<String,FormField> parseMultiPost(HttpServletRequest req) throws IOException, ServletException {
    req.getServletContext().setAttribute("javax.servlet.context.tempdir",new File(System.getProperty("java.io.tmpdir")));
    
    Collection<Part> p = req.getParts();
    Map<String,FormField> rp = new HashMap<String,FormField>();
    for(Part z : p) { 
      rp.put( z.getName(), new FormField( z ));
    }
    return rp;
  }
  
  private Map<String, FormField> parsePost(HttpServletRequest req) throws ServerException, ServletException, IOException {
    Map<String, FormField> paramMap = isMultipartContent(req) ?
      parseMultiPost(req) 
      : parseBasicPost(req);
    return paramMap;
  }
  private void parsePostLine(String line, Map<String, FormField> paramMap) {
    if (line.length() == 0) { return; }
    int sep = line.indexOf("=");
    if (sep == -1) {
      paramMap.put(line, null);
    } else {
      String key = line.substring(0, sep);
      String value = line.substring(sep + 1);
      paramMap.put(key, new FormField(value));
    }
  }

  private static boolean isMultipartContent(HttpServletRequest request) {
    if (!"post".equals(request.getMethod().toLowerCase())) { return false; }
    String contentType = request.getContentType();
    if (contentType == null) { return false; }
    if (contentType.toLowerCase().startsWith("multipart/")) { return true; }
    return false;
  }
  
  
  
  ////////////////////////////////////////////////////////
  
  public void setMarshallFailure(Throwable ex) {
    this.exception = ex;
    this.method = null;
    this.parameters = null;
  }

  public void setParameters(Object[] parameters) {
    this.parameters = parameters;
  }

  public void findMethod() throws ClassNotFoundException {
    if (scriptName == null) { throw new IllegalArgumentException("Missing class parameter"); }
    if (methodName == null) { throw new IllegalArgumentException("Missing method parameter"); }

    int inputArgCount = ov.getParameterCount();
    // Get a mutable list of all methods on the type specified by the creator
    RemoteRequestProxy module = RemoteRequestProxy.getModule(scriptName, null, null);
    List<Method> allMethods = new ArrayList<Method>();
    for (Method m : module.getMethods()) { // only use methods with
                                           // matching name
      if (m.getName().equals(methodName)) allMethods.add(m);
    }

    if (allMethods.isEmpty()) {
      // Not even a name match
      throw new IllegalArgumentException("Method name not found. See logs for details");
    }

    // Remove all the methods where we can't convert the parameters
    List<Method> am = new ArrayList<Method>();
    allMethodsLoop: for (Method m : allMethods) {
      Class<?>[] methodParamTypes = m.getParameterTypes();

      // Remove non-varargs methods which declare less params than were passed
      if (!m.isVarArgs() && methodParamTypes.length < inputArgCount) continue allMethodsLoop;

      // Remove methods where we can't convert the input
      for (int i = 0; i < methodParamTypes.length; i++) {
        Class<?> methodParamType = methodParamTypes[i];
        Object param = ov.getParameter(i);
        if (param != null && param.getClass() == FileTransfer.class) {
          param = ((FileTransfer)param).asObject();
          ov.setParameter(i, param);
        }
        
        Class<?> inputType = param == null ? null : param.getClass(); // param.getClientDeclaredType();

        // If we can't convert this parameter type, ignore the method
        // FIXME: I should really figure out if the thing is convertable
        // Actually, don't worry about it: if we can't convert the parameter
        // type, we'll get an error later
        // if (inputType == null &&
        // !c.converterManager.isConvertable(methodParamType)) continue
        // allMethodsLoop;

        // Remove methods which declare more non-nullable parameters than were
        // passed
        if (inputArgCount <= i && methodParamType.isPrimitive()) continue allMethodsLoop;

        boolean ok = false;
        // Remove methods where the client passed a type and we can't use it.
        if (methodParamType.isPrimitive()) {
          methodParamType = Array.get( Array.newInstance(methodParamType, 1), 0).getClass();
        }
        if (inputType == null || methodParamType.isAssignableFrom(inputType)) ok = true;
        else {
          Constructor<?>[] cc = methodParamType.getConstructors();
          for(Constructor<?> c : cc) {
            Class<?>[] cls = c.getParameterTypes();
            if (cls.length == 1 && cls[0].isAssignableFrom(inputType)) { ok = true;
            try { param = c.newInstance(param); ov.setParameter(i, param); } catch(Exception ex) { ok = false; }
            if (ok) break; }
          }
        }
        if (!ok) continue allMethodsLoop;

        /** Added to increase our ability to call overloaded methods
         * accurately! Is the inbound JavaScript type assignable to
         * methodParamType? We are limited to what JavaScript gives us
         * (number, date, boolean, etc.) We only want to performn this if
         * there are multiple methods with the same name (hack for now). */
        if (allMethods.size() > 1) {
          String javaScriptType = param.getClass().getName(); // getType();
          JavascriptType jst = JavascriptType.valueOf(javaScriptType + "T");

          // If this method takes a vararg, the JavaScript type being passed
          // is not
          // an array, and this is the var argument we need to use the
          // component type of the argument.
          // Otherwise this method will be removed because javaScriptType (not
          // array) and methodParamType
          // (Array - this is the vararg) won't match.
          if (m.isVarArgs() && JavascriptType.arrayT != jst && i == methodParamTypes.length - 1) {
            methodParamType = methodParamType.getComponentType();
          }
          if (!isJavaScriptTypeAssignableTo(jst, methodParamType)) continue allMethodsLoop;
          /** end overloaded section */
        }
      }
      am.add(m);
    }

    if (am.isEmpty()) {
      // Not even a name match
      throw new IllegalArgumentException("Method not found. See logs for details");
    } else if (am.size() == 1) {
      method = am.get(0);
      return;
    }

    // If we have methods that exactly match the param count we use a
    // different matching algorithm, to when we don't
    List<Method> exactParamCountMatches = new ArrayList<Method>();
    for (Method m : am) {
      if (!m.isVarArgs() && m.getParameterTypes().length == inputArgCount) {
        exactParamCountMatches.add(m);
      }
    }

    if (exactParamCountMatches.size() == 1) {
      // One method with the right number of params - use that
      method = exactParamCountMatches.get(0);
      return;
    } else if (exactParamCountMatches.size() == 2) {
      // Two methods with same name, the correct number of parameters and
      // convertible inputs.
      int choose = -1;
      boolean compatible = true;
      Class<?>[] params1 = exactParamCountMatches.get(0).getParameterTypes();
      Class<?>[] params2 = exactParamCountMatches.get(1).getParameterTypes();
      for (int i = 0; i < params1.length; i++) {
        if (compatible && !params1[i].equals(params2[i])) {
          if (choose < 0) {
            choose = params1[i].isAssignableFrom(params2[i]) ? 1 : 0;
          }
          compatible &= ((choose == 1) || params2[i].isAssignableFrom(params1[i]));
        }
      }
      if (compatible && (choose >= 0)) {
        method = exactParamCountMatches.get(choose); // Select the
                                                     // most
                                                     // concrete of
                                                     // the two
        return;
      }
    }

    // Lots of methods with the right name, but none with the right
    // parameter count. If we have exactly one varargs method, then we
    // try that, otherwise we bail.
    List<Method> varargsMethods = new ArrayList<Method>();
    for (Method m : am) {
      if (m.isVarArgs()) {
        varargsMethods.add(m);
      }
    }

    if (varargsMethods.size() == 1) {
      method = varargsMethods.get(0);
      return;
    }

    throw new IllegalArgumentException("Method not found. See logs for details");
  }

  private enum JavascriptType {
    arrayT, booleanT, numberT, stringT, dateT, nullT, enumT, Object_ObjectT
  }

  /** Determines if a JavaScript type is assignable to the passed in Java
   * class.
   * 
   * @param javaScriptType
   * @param clazz
   * @return boolean */
  private static boolean isJavaScriptTypeAssignableTo(JavascriptType jst, Class<?> clazz) {
    switch (jst) {
    case arrayT:
      return isJavaScriptArrayConvertableTo(clazz);
    case booleanT:
      return isJavaScriptBooleanConvertableTo(clazz);
    case numberT:
      return isJavaScriptNumberConvertableTo(clazz);
    case stringT:
      return isJavaScriptStringConvertableTo(clazz);
    case dateT:
      return Date.class.isAssignableFrom(clazz);
    case Object_ObjectT:
      return isJavaScriptObjectConvertableTo(clazz);
    default:
      return true;
    }
  }

  /** Can a JavaScript "object" be converted to type?
   * 
   * @param type
   * @return boolean */
  private static boolean isJavaScriptObjectConvertableTo(Class<?> type) {
    return !(isTypeSimplyConvertable(type) || isJavaScriptArrayConvertableTo(type));
  }

  /** Can a JavaScript "boolean" be converted to type?
   * 
   * @param type
   * @return boolean */
  private static boolean isJavaScriptBooleanConvertableTo(Class<?> type) {
    return (boolean.class.isAssignableFrom(type) || Boolean.class.isAssignableFrom(type));
  }

  private static final List<?> TYPES_COMPATIBLE_WITH_JS_NUMBER = Arrays.asList(new Class[] { Byte.TYPE, Byte.class, Short.TYPE,
      Short.class, Integer.TYPE, Integer.class, Long.TYPE, Long.class, Float.TYPE, Float.class, Double.TYPE, Double.class,
      BigDecimal.class, BigInteger.class });

  private static boolean isJavaScriptArrayConvertableTo(Class<?> type) {
    return type.isArray() || Collection.class.isAssignableFrom(type);
  }

  private static boolean isJavaScriptNumberConvertableTo(Class<?> type) {
    return TYPES_COMPATIBLE_WITH_JS_NUMBER.contains(type);
  }

  private static boolean isJavaScriptStringConvertableTo(Class<?> type) {
    return String.class.isAssignableFrom(type) || char.class.equals(type) || Locale.class.equals(type)
        || Currency.class.equals(type);
  }

  private static boolean isTypeSimplyConvertable(Class<?> paramType) {
    return paramType == String.class || paramType == Integer.class || paramType == Integer.TYPE || paramType == Short.class
        || paramType == Short.TYPE || paramType == Byte.class || paramType == Byte.TYPE || paramType == Long.class
        || paramType == Long.TYPE || paramType == Float.class || paramType == Float.TYPE || paramType == Double.class
        || paramType == Double.TYPE || paramType == Character.class || paramType == Character.TYPE || paramType == Boolean.class
        || paramType == Boolean.TYPE;
  }

  @Override public String toString() {
    return scriptName + "." + methodName + "(...)";
  }
  
  
  public void marshallInbound() {
      try {
        findMethod();
        if (method == null) {
          throw new IllegalArgumentException("Missing method or missing parameter converters");
        }

        // We are now sure we have the set of inputs lined up. They may
        // cross-reference so we do the de-referencing all in one go.
        // TODO: should we do this here? - why not earlier?
        // do we need to know the method before we dereference?
        //ov.dereference();

        // Convert all the parameters to the correct types
        int destParamCount = method.getParameterTypes().length;
        Object[] arguments = new Object[destParamCount];
        for (int j = 0; j < destParamCount; j++) {
          Object param = null;
          if (method.isVarArgs() && j + 1 == destParamCount) {
 //           param = ov.createArrayWrapper( destParamCount);
          } else {
            param = ov.getParameter( j);
          }

          Type paramType = method.getGenericParameterTypes()[j];
          arguments[j] = Cast.cast(param,paramType);
        }

        setParameters(arguments);
      } catch (Exception ex) {
        setMarshallFailure(ex);
      }
 
  }
  
  public Object execute(HttpServletResponse response) {
    try {
      RemoteRequestProxy module = RemoteRequestProxy.getModule(scriptName, request, response);

      // Do we already have an error?
      if (method == null || exception != null) {
        return exception;
      }

      return method.invoke(module, parameters);
    } catch (InvocationTargetException ex) {
      // Allow Jetty RequestRetry exception to propagate to container
      // Continuation.rethrowIfContinuation(ex);
      return ex.getTargetException();
    } catch (Exception ex) {
      // Allow Jetty RequestRetry exception to propagate to container
      // Continuation.rethrowIfContinuation(ex);
      return ex;
    }
  }
 
}