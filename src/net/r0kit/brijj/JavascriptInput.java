package net.r0kit.brijj;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletRequest;


public class JavascriptInput implements ObjectInput {

  Map<String,FormField> extraParameters;
  HttpServletRequest request;
  
  public JavascriptInput(Map<String,FormField> arg, HttpServletRequest q) {
    extraParameters = arg;
    request = q;
  }
  
  
  
  @Override public boolean readBoolean() throws IOException {
    // TODO Auto-generated method stub
    return false;
  }

  @Override public byte readByte() throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override public char readChar() throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override public double readDouble() throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override public float readFloat() throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override public void readFully(byte[] arg0) throws IOException {
    // TODO Auto-generated method stub
    
  }

  @Override public void readFully(byte[] arg0, int arg1, int arg2) throws IOException {
    // TODO Auto-generated method stub
    
  }

  @Override public int readInt() throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override public String readLine() throws IOException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override public long readLong() throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override public short readShort() throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override public String readUTF() throws IOException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override public int readUnsignedByte() throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override public int readUnsignedShort() throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override public int skipBytes(int arg0) throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override public int available() throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override public void close() throws IOException {
    // TODO Auto-generated method stub
    
  }

  @Override public int read() throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override public int read(byte[] arg0) throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }

  @Override public int read(byte[] arg0, int arg1, int arg2) throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }

  public Object readObject(String t, String v) {
    if (t.equals("string")) return urlDecode(v);
    if (t.equals("number")) {
      try { return Integer.parseInt(v); }
      catch(NumberFormatException nfe) {}
      try { return Long.parseLong(v); }
      catch(NumberFormatException nfe) {}
      try { return Double.parseDouble(v); }
      catch(NumberFormatException nfe) {}      
    }
    if (t.equals("boolean")) return Boolean.parseBoolean(v);
    if (t.equals("array")) {
      LinkedList<Object> ll = new LinkedList<Object>();
      StringTokenizer st = new StringTokenizer(v, ",");
      while(st.hasMoreElements()) {
        String z = urlDecode(st.nextToken());
        TypeValue tv = splitInbound(z);
        ll.add(readObject(tv.type, tv.value));
      }
      return ll;
    }
    if (t.startsWith("Object_")) {
      HashMap<String,Object>hm = new HashMap<String,Object>();
      StringTokenizer st = new StringTokenizer( v.substring(1,v.length()-1), ",");
     
      while(st.hasMoreElements()) {
        String z = urlDecode(st.nextToken());
        TypeValue tv = splitInbound(z);
        TypeValue ttv = splitInbound(tv.value);
        hm.put(tv.type.trim(), readObject(ttv.type, ttv.value));
      }
      return hm;
    }
    else return null;
  }
  
  public static class TypeValue {
    public String type;
    public String value;
    public TypeValue(String v) {
      type = "string";
      value = v;
    }
    public TypeValue(String t, String v) {
      type = t; value = v;
    }
  }
  public static TypeValue splitInbound(String data) {
    int colon = data.indexOf(':');
    return colon == -1 ? new TypeValue(data) : new TypeValue(data.substring(0, colon), data.substring(colon+1));
  }

  
  
  // ImageIO.read(ff.getInputStream());
  
  
  @Override public Object readObject() throws ClassNotFoundException, IOException {
    
    Map<String,Object> inboundContext = new HashMap<String,Object>();
    
    for (Iterator<Map.Entry<String, FormField>> it = extraParameters.entrySet().iterator(); it.hasNext();) {
      Map.Entry<String, FormField> entry = it.next();
      String key = entry.getKey();
      FormField formField = entry.getValue();
      if (formField.isFile()) {
        inboundContext.put(key,  new FileTransfer(formField, request));
      } else {
        TypeValue split = splitInbound(formField.getString());
        inboundContext.put(key,  readObject(split.type, split.value));
//        inboundContext.createInboundVariable(key, split);
      }
    }
    
    return inboundContext;
  }

  @Override public long skip(long arg0) throws IOException {
    // TODO Auto-generated method stub
    return 0;
  }
  
 
  public static String urlDecode(String value) {
    try {
      return URLDecoder.decode(value, "UTF-8");
    } catch (UnsupportedEncodingException ignore) {
      return value;
    }
  }
}
