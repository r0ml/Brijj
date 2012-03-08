
package net.r0kit.brijj;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.servlet.http.Part;

public class FormFieldx {
  public FormFieldx(String string) {
    this.createdWith = string;
    this.name = null;
    this.mimeType = null;
    // FIXME:  do the encoding right
    byte[] bytes = string.getBytes();
    this.inputStream = new ByteArrayInputStream(bytes);
    this.fileSize = bytes.length;
  }
  public FormFieldx(Part z) {
    this.name = z.getName();
    this.mimeType = z.getContentType();
    this.fileSize = z.getSize();
    isFile = z.getHeader("content-disposition").contains("filename=");
    try { this.inputStream = z.getInputStream(); }
    catch(IOException iox) {}
  }

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
  @Override public String toString() {
    return "FormField:" + ( createdWith == null ? name : createdWith );
  }
  public long fileSize;
  public final String name;
  public String mimeType;
  public InputStream inputStream;
  public boolean isFile;
  private String createdWith;
  private boolean hasBeenRead = false;
}
