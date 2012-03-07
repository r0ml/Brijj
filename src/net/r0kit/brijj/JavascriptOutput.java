package net.r0kit.brijj;

import java.io.IOException;
import java.io.ObjectOutput;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Array;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.http.HttpServletResponse;


public class JavascriptOutput implements ObjectOutput {
  HttpServletResponse response;
  boolean iframe;
  String batchId;
  private static Set<String> reserved = new TreeSet<String>();
  
  static {
    String rw = "as break case catch class const continue default delete"
        + " do else export extends false finally for function if import in"
        + " instanceof is namespace new null package private public return"
        + " super switch this throw true try typeof use var void while with" +
        // Reserved for future use at ECMAScript 4
        " abstract debugger enum goto implements interface native protected" + "synchronized throws transient volatile" +
        // Reserved in ECMAScript 3, unreserved at 4 best to avoid anyway
        "boolean byte char double final float int long short static";
  
    reserved.addAll(Arrays.asList(rw.split(" ")));
  }
  public JavascriptOutput(HttpServletResponse r, boolean i, String id) {
    response = r;
    iframe = i;
    batchId = id;
  }

  @Override public void writeBoolean(boolean arg0) throws IOException {
    // TODO Auto-generated method stub
    
  }

  @Override public void writeByte(int arg0) throws IOException {
    // TODO Auto-generated method stub
    
  }

  @Override public void writeBytes(String arg0) throws IOException {
    // TODO Auto-generated method stub
    
  }

  @Override public void writeChar(int arg0) throws IOException {
    // TODO Auto-generated method stub
    
  }

  @Override public void writeChars(String arg0) throws IOException {
    // TODO Auto-generated method stub
    
  }

  @Override public void writeDouble(double arg0) throws IOException {
    // TODO Auto-generated method stub
    
  }

  @Override public void writeFloat(float arg0) throws IOException {
    // TODO Auto-generated method stub
    
  }

  @Override public void writeInt(int arg0) throws IOException {
    // TODO Auto-generated method stub
    
  }

  @Override public void writeLong(long arg0) throws IOException {
    // TODO Auto-generated method stub
    
  }

  @Override public void writeShort(int arg0) throws IOException {
    // TODO Auto-generated method stub
    
  }

  @Override public void writeUTF(String arg0) throws IOException {
    // TODO Auto-generated method stub
    
  }

  @Override public void close() throws IOException {
    // TODO Auto-generated method stub
    
  }

  @Override public void flush() throws IOException {
    // TODO Auto-generated method stub
    
  }

  @Override public void write(int b) throws IOException {
    // TODO Auto-generated method stub
    
  }

  @Override public void write(byte[] b) throws IOException {
    // TODO Auto-generated method stub
    
  }

  @Override public void write(byte[] b, int off, int len) throws IOException {
    // TODO Auto-generated method stub
    
  }

  private void writeThrowable(Throwable t) throws IOException {
    PrintWriter p = response.getWriter();
    setCT();
    p.write(outboundPrefix());
    p.write("//#BRIJJ-REPLY\r\n");
    p.write("r.handleException(");
    p.write("\""+batchId+"\"");
    p.write(",{javaClassName:\"");
    p.write(t.getClass().getName());
    p.write("\",message:\"");
    p.write(escapeJavaScript(t.getMessage()));
    p.write("\"}\r\n);");
    p.write(outboundSuffix());
  }
  
  private static String escapeJavaScript(String str) {
    if (str == null) return null;
    StringBuffer writer = new StringBuffer(str.length() * 2);
    int sz = str.length();
    for (int i = 0; i < sz; i++) {
      char ch = str.charAt(i);
      switch (ch) {
      case '\b':
        writer.append("\\b");
        break;
      case '\n':
        writer.append("\\n");
        break;
      case '\t':
        writer.append("\\t");
        break;
      case '\f':
        writer.append("\\f");
        break;
      case '\r':
        writer.append("\\r");
        break;
      case '\'':
        writer.append("\\'");
        break;
      case '"':
        writer.append("\\\"");
        break;
      case '\\':
        writer.append("\\\\");
        break;
      default:
        if (ch >= 0x20 && ch < 0x80) {
          writer.append(ch);
        } else {
          writer.append("\\u");
          String s = Integer.toHexString(ch).toUpperCase(Locale.ENGLISH);
          int n = 4 - s.length();
          while (n-- > 0)
            writer.append('0');
          writer.append(s);
        }
      }
    }
    return writer.toString();
  }

  private void setCT() { 
    response.setContentType(iframe ? "text/html" : "text/javascript; charset=\"utf=8\"");
  }
  private void downloadFile(FileTransfer ft) throws IOException {
    PrintWriter p = response.getWriter();
    setCT();
    p.write(outboundPrefix());
    p.write("//#BRIJJ-REPLY\r\n");
    p.write("r.handleDownload(");
    p.write("\""+batchId+"\",");
    p.write(ft.getPath());
    p.write("\r\n);");
    p.write(outboundSuffix());
  }
  @Override public void writeObject(Object obj) throws IOException {
/*    if (obj == null) return new NonNestedOutboundVariable("null", context);

    // Check to see if we have done this one already
    OutboundVariable ov = context.get(data);
    // So the object as been converted already, we just need to refer to it.
    if (ov != null) return ov.getReferenceVariable();
    Class<?> dc = data.getClass();
    if (dc.isArray()) return convertOutboundArray(data);
    else if (dc.isEnum()) return new NonNestedOutboundVariable('\'' + ((Enum<?>) data).name() + '\'', context);
    else if (isPrimitiveClass(dc)) return convertOutboundPrimitive(data);
    else if (isStructurelessClass(dc)) return new NonNestedOutboundVariable(data.toString(), context);
    else if (URL.class.isAssignableFrom(dc)) {
      String escaped = JavascriptUtil.escapeJavaScript(((URL) data).toExternalForm());
      return new NonNestedOutboundVariable('\"' + escaped + '\"', context);
    } else if (data instanceof Date || data instanceof Calendar) return convertOutboundDate(data);
    // SQLException is Iterable, so check the Throwable first
    else if (Map.class.isAssignableFrom(dc)) return convertOutboundMap((Map<Object, Object>) data);
    else if (isEscapedClass(dc)) return new NonNestedOutboundVariable(
        '\"' + JavascriptUtil.escapeJavaScript(data.toString()) + '\"', context);
    else if (Iterator.class.isAssignableFrom(dc)) return convertOutboundIterator((Iterator<Object>) data);
    else if (Iterable.class.isAssignableFrom(dc)) return convertOutboundIterator(((Iterable<Object>) data).iterator());
    else if (dc.getAnnotation(Brijj.RemoteData.class) != null) return convertOutboundObject(data);
    else if (isDownloadClass(dc)) return convertOutboundFile(data);
    else if (Currency.class.isAssignableFrom(dc)) {
      return new NonNestedOutboundVariable('\"' + ((Currency) data).getCurrencyCode() + '\"', context);
    } else if (dc == char[].class) {
      return new NonNestedOutboundVariable('\"' + JavascriptUtil.escapeJavaScript(new String((char[]) data)) + '\"', context);
    }
    else if ( Convertible.class.isAssignableFrom(dc)) {
      return convertOutbound( ((Convertible)data).convert());
    }
    // else if ( Collection.class.isAssignableFrom(dc)) return
    // convertOutboundIterator(((Collection<Object>)data).iterator(),
    // converted);
    else throw new ConversionException(dc, "unknown outbound conversion");

    // return converter.convertOutbound(data, converted);

*/
    
    
    
    
    
    if (obj instanceof Throwable) { 
      writeThrowable( (Throwable) obj);
      return;
    } 
    
/*    if (obj instanceof FileTransfer && false) {
      downloadFile( (FileTransfer) obj);
      return;
    }
*/
    
    PrintWriter p = response.getWriter();
    setCT();
    p.write(outboundPrefix());
    p.write("//#BRIJJ-REPLY\r\n");
    p.write("r.handleCallback(");
    p.write("\""+batchId+"\",");


    writeAnObject(obj, p);
    p.write("\r\n);");
    p.write(outboundSuffix());

  }
  
  private void writeDate(Calendar obj, PrintWriter p) {
    long millis = obj.getTime().getTime();
    p.write("new Date("+millis+")");
  }
  
  private void writeDate(Date obj, PrintWriter p) {
    p.write("new Date(" + obj.getTime() + ")");
  }

  @SuppressWarnings("unchecked") private void writeAnObject(Object obj, PrintWriter p) throws IOException {
    if (obj == null) p.write("null");
    else if (obj.getClass().isArray()) writeArray( obj, p);
    else if (obj instanceof String) p.write("\""+escapeJavaScript((String)obj)+"\"");
    else if (obj instanceof Iterable) writeIterator( ((Iterable<Object>) obj).iterator(), p);
    else if (obj instanceof Iterator) writeIterator( (Iterator<Object>) obj, p);
    else if (obj instanceof Remotable) writeAnObject( ( (Remotable)obj).toRemote(), p);
    else if (obj instanceof Map) writeMap( (Map<String,Object>)obj, p);
    else if (obj instanceof Integer) writeNumber( (Integer)obj, p);
    else if (obj instanceof Boolean) writeBoolean( (Boolean) obj, p);
    else if (obj instanceof FileTransfer) p.write( ((FileTransfer)obj).getPath() );
    else if (obj instanceof Calendar) writeDate((Calendar)obj, p);
    else if (obj instanceof Date) writeDate((Date)obj, p);
    else if (obj instanceof Long) writeNumber((Long)obj, p);
    else if (obj instanceof Double) writeNumber((Double)obj, p);
    else if (obj instanceof Float) writeNumber((Float)obj, p);
    else if (obj instanceof Short) writeNumber((Short)obj, p);
    else if (obj.getClass().isEnum()) p.write("'" + ((Enum<?>) obj).name() + "'");
    else p.write("???");
  }
  private void writeBoolean(Boolean b, PrintWriter p) {
    if (b) p.write("true"); else p.write("false");
  }
  private void writeNumber(double d, PrintWriter p) {
    p.write(Double.toString(d));
  }
  private void writeNumber(long n, PrintWriter p) {
    p.write(Long.toString(n));
  }
  private void writeNumber(Integer i, PrintWriter p) {
    p.write(Integer.toString(i));
  }
  private void writeMap( Map<String,Object> obj, PrintWriter p) throws IOException {
    p.write("{");
    for(Map.Entry<String,Object> e : obj.entrySet()) {
      p.write("\""+escapeJavaScript(e.getKey())+"\":");
      writeAnObject(e.getValue(), p);
      p.write(",\r\n");
    }
    p.write("}");
  }
  
  private void writeIterator(Iterator<Object> obj, PrintWriter p) throws IOException {
    p.write("[");
    while( obj.hasNext()) {
      writeAnObject(obj.next(), p);
      p.write(",\r\n");
    }
    p.write("]");
  }
  private void writeArray(Object obj,PrintWriter p) throws IOException {
    p.write("[");
    int n = Array.getLength(obj);
    for(int i=0;i<n;i++) {
      Object o = Array.get(obj,i);
      writeAnObject(o,p);
      p.write(",\r\n");
    }
    p.write("]");
  }
  
  private String outboundPrefix() {
    String pfx = "(function(){\nvar r=window.brijj._;\n";
    // Send the script prefix (if any)
    if (iframe) {
      pfx = "<html><body><script type='text/javascript'>(function(){\ntry{\nvar r=window.parent.brijj._;\nr.beginIFrameResponse(this.frameElement"
          +(batchId == null?"":", '" + batchId+"'") + ");\r\n";
    }
    return pfx;
  }
  private String outboundSuffix() {
  String sfx = "})();\n";
  if (iframe) { sfx = "r.endIFrameResponse("+(batchId == null?"":"'" + batchId+"'")
      +");} catch(ex) {if (!(ex.number && ex.number == -2146823277)) { throw ex; }} })();\n</script></body></html>";
  }
  return sfx;
  }

  /** URL decode a value. {@link URLDecoder#decode(String, String)} throws an
   * {@link UnsupportedEncodingException}, which is silly given that the most
   * common use case will be to pass in "UTF-8"
   * 
   * @param value
   *          The string to decode
   * @return The decoded string */


  public static boolean isReserved(String name) {
    return reserved.contains(name);
  }


}
