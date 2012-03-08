package net.r0kit.brijj;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.r0kit.brijj.Brijj.Remotable;

public class Json {
  
  static SimpleDateFormat _sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  
  public static String writeObject(Object xa) {
	  StringWriter sw = new StringWriter();
	  try { writeObject(xa, sw); }
	  catch(IOException iox) {}
	  return sw.toString();
  }
  // Convert a Java object to JSON

  private static Map<Character,String> replacements = 
      new HashMap<Character,String>();
  static {
    replacements.put('\b',"\\b");
    replacements.put('\n',"\\n");
    replacements.put('\t',"\\t");
    replacements.put('\f',"\\f");
    replacements.put('\r',"\\r");
    replacements.put('\'',"\\'");
    replacements.put('"',"\\\"");
    replacements.put('\\',"\\\\");
  }
  public static String escapeJavaScript(String str) {
    if (str == null) return "null";
    StringBuffer writer = new StringBuffer(str.length() * 2);
    int sz = str.length();
    for (int i = 0; i < sz; i++) {
      char ch = str.charAt(i);
      String rpl = replacements.get(ch);
      if (rpl != null) writer.append(rpl);
      else if (ch >= 0x20 && ch < 0x80) writer.append(ch);
      else {
          writer.append("\\u");
          String s = Integer.toHexString(ch).toUpperCase(Locale.ENGLISH);
          int n = 4 - s.length();
          while (n-- > 0)
            writer.append('0');
          writer.append(s);
        }
      }
    return writer.toString();
  }

  
  @SuppressWarnings("unchecked")
  public static void writeObject(Object xa, Writer w) throws IOException {
    Object x = xa;
    if (x instanceof Remotable) x = ((Remotable)x).toRemote();
    if (x == null) { w.write("null"); return; } // undefined?
    if (x instanceof Character) x = ((Character)x).toString();
    if (x instanceof byte[]) x = new String((byte[])x); 
    if (x instanceof String) { w.write("\""+escapeJavaScript((String)x)+"\""); return; }
    if (x instanceof Calendar) x = ((Calendar)x).getTime();
    if (x instanceof Date) {
      w.write("new Date(" + ((Date)x).getTime() + ")"); return; }
    //  w.write("\""+_sdf.format((Date) x)+"\""); return; }
    if (x instanceof Integer || x instanceof Boolean || x instanceof Double || x instanceof Long || x instanceof Short
        || x instanceof Float) { w.write(x.toString()); return; }
    if (x.getClass().isEnum()) { writeObject(x.toString(),w); return;}
    if (x.getClass().isArray()) {
      w.write("([");
      for(int i=0;i<Array.getLength(x);i++) { if (i>0) w.write(","); writeObject(Array.get(x,i),w); }
      w.write("])");
      return;
    }
    if (x instanceof Iterable) x = ((Iterable<Object>)x).iterator();
    if (x instanceof Iterator) {
      Iterator<Object> i = (Iterator<Object>)x;
      w.write("([");
      boolean first = true;
      while (i.hasNext()) {
        if (first) first = false; else w.write(",");
        writeObject(i.next(), w);
      }
      w.write("])");
      return;
    }
    if (x instanceof Map) {
      Map<String,Object>xx = (Map<String,Object>)x;
      w.write("({"); 
      boolean firstOne = true;
      for(Object key : xx.keySet()) {
        if (firstOne) firstOne = false; else w.write(",");
        writeObject( key, w);
        w.write(":");
        writeObject(xx.get(key),w);
      }
      w.write("})"); 
      return;
    }
    if (x instanceof FileTransfer && ((FileTransfer)x).mimeType.startsWith("image/")) {
      w.write("(function() {var z = new Image(); z.src="+((FileTransfer)x).getPath()+"; return z; })()");
      return;
    }
    if (x instanceof FileTransfer) { w.write(((FileTransfer)x).getPath()); return; }
    throw new RuntimeException("unable to convert to Json: "+x.getClass());
  }

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
  public static boolean isReserved(String name) {
    return reserved.contains(name);
  }

  
}
