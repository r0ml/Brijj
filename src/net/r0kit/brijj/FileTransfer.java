package net.r0kit.brijj;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.imageio.ImageIO;
import javax.servlet.http.HttpServletRequest;

public class FileTransfer {
  public FileTransfer(BufferedImage image, String type) {
    this(image, null, type, null);
  }
  public FileTransfer(BufferedImage image, String type, HttpServletRequest req) {
    this(image, null, type, req);
  }
  /** A ctor for the 3 things browsers tell us about the uploaded file */
  public FileTransfer(final BufferedImage image, String f, final String type, HttpServletRequest req) {
    this.req = req;
    this.filename = f;
    this.mimeType = "image/" + type;
    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      ImageIO.write(image, type, bos);
      this.inputStream = new ByteArrayInputStream(bos.toByteArray());
      this.size = bos.size();
    } catch (IOException iox) {
      this.size = -1;
      Logger.getLogger(getClass().getName()).log(Level.WARNING, "creating image input stream: " + iox);
    }
  }
  public FileTransfer(String f, String m, final byte[] bytes, HttpServletRequest req) {
    this.req = req;
    this.filename = f;
    this.mimeType = m;
    this.size = bytes.length;
    this.inputStream = new ByteArrayInputStream(bytes);
  }
  public String getPath() {
    int z = hashCode();
    timeSaved = System.currentTimeMillis();
    instances.put(z, this);
    return "'" +  ( req == null ? "" : (req.getContextPath() + req.getServletPath() + "/")) +"download/" + z + "'";
  }
  public InputStream getInputStream() {
    if (inputStream != null) return inputStream;
    else return null;
  }

  public final String filename;
  public final String mimeType;
  public long size;
  public InputStream inputStream;

  public static FileTransfer get(int z) {
    FileTransfer t = instances.get(z);
    if (t != null) instances.remove(z);
    return t;
  }
  public BufferedImage asImage() {
    try {
      return ImageIO.read(getInputStream());
    } catch (IOException ioe) {
      return null;
    }
  }
  public String asString() {
    try {
      return new String(getBytes());
    } catch (IOException ioe) {
      return null;
    }
  }
  private byte[] getBytes() throws IOException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    byte[] buffer = new byte[4096];
    InputStream i = getInputStream();
    while (true) {
      int n = i.read(buffer);
      if (n <= 0) break;
      bos.write(buffer, 0, n);
    }
    return bos.toByteArray();
  }
  public Object asObject() {
    if (mimeType.startsWith("text/")) return asString();
    else if (mimeType.startsWith("image/")) return asImage();
    else return this;
  }

  public boolean inline = true;
  public long timeSaved;
  private HttpServletRequest req;
  private static Map<Integer, FileTransfer> instances = new HashMap<Integer, FileTransfer>();
}
