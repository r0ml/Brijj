package net.r0kit.brijj;

public class ServerException extends Exception {
  private static final long serialVersionUID = -707126214405629313L;

  public ServerException(String message) {
    super(message);
  }
  public ServerException(String message, Throwable ex) {
    super(message, ex);
  }
  public ServerException(Throwable ex) {
    super(ex.getMessage(), ex);
  }
}
