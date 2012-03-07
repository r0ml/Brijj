
package net.r0kit.brijj;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.servlet.http.Part;


public class FormField {
  private String createdWith;
  private boolean hasBeenRead = false;

  public FormField(String string) {
    this.createdWith = string;
    this.name = null;
    this.mimeType = null;
    // FIXME:  do the encoding right
    byte[] bytes = string.getBytes();
    this.inputStream = new ByteArrayInputStream(bytes);
    this.fileSize = bytes.length;
  }
  
  public FormField(Part z) {
    this.name = z.getName();
    this.mimeType = z.getContentType();
    this.fileSize = z.getSize();
    isFile = z.getHeader("content-disposition").contains("filename=");
    try { this.inputStream = z.getInputStream(); }
    catch(IOException iox) {}
  }

  public String getMimeType() {
    return mimeType;
  }

  // FIXME: shouldn't size be set at instantiation?
  public long getFileSize() {
     return fileSize;
  }

  public InputStream getInputStream() {
    return inputStream;
  }

  public String getName() {
    return name;
  }

  /** Returns the contents of the file item as a String. */
  public String getString() {
    if (hasBeenRead == true) {
      throw new RuntimeException("reading a FormField twice should never happen -- if it does, the inputStream needs to be reset");
    }
    hasBeenRead = true;
      try {
          return Brijj.readAllTextFrom(new InputStreamReader(inputStream,"UTF-8"));
      } catch (IOException ex) {
        return null;
      }
    }

  public byte[] getBytes() {
     try { return Brijj.readAllBytesFrom(inputStream); }
     catch(IOException ex) { return null; }
  }
  
  public boolean isFile() {
    return isFile ;
  }

  @Override public String toString() {
    return "FormField:" + ( createdWith == null ? name : createdWith );
  }

  private long fileSize;

  private final String name;

  private String mimeType;

  private InputStream inputStream;
  private boolean isFile;
  
}
