package net.r0kit.brijj.servlet;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import net.r0kit.brijj.Brijj;
import net.r0kit.brijj.RemoteRequestProxy;

public class __System extends RemoteRequestProxy {

  public __System(HttpServletRequest q, HttpServletResponse s) {
    super(q, s);
  }

  /** Generates and returns a new unique id suitable to use for the CSRF session
   * cookie. This method is itself exempted from CSRF checking. */
  @Brijj.RemoteMethod public String generateId() {
    return generate();
  }

     // is this sufficient?
    static String nextSessionId() {
      return new BigInteger(130, random).toString(32);
    }

    protected static SecureRandom random ;
    protected static int countSinceSeed = 0;
    protected static long seedTime = 0;
    protected static int countSinceTimeChange = 0;
    protected static long lastGenTime = 0;

  static {
      // SecureRandom implements a cryptographically secure pseudo-random
      // number generator (PRNG).
      // We want Sun's SHA1 algorithm on all platforms.
      // (see
      // http://www.cigital.com/justiceleague/2009/08/14/proper-use-of-javas-securerandom/)

      random = null;
      
      // Try Sun's SHA1
      try {
        random = SecureRandom.getInstance("SHA1PRNG", "SUN");
      } catch (NoSuchAlgorithmException ex) { /* squelch */} catch (NoSuchProviderException ex) { /* squelch */}

      // Try any SHA1
      try {
        if (random == null) {
          random = SecureRandom.getInstance("SHA1PRNG");
        }
      } catch (NoSuchAlgorithmException ex) { /* squelch */}

      // Fall back to default
      if (random == null) {
        random = new SecureRandom();
      }

      // Now seed the generator
      reseed();
    }

    /** Generates an id string guaranteed to be unique for eternity within the
     * scope of the running server, as long as the real-time clock is not adjusted
     * backwards. The generated string consists of alphanumerics (A-Z, a-z, 0-9)
     * and symbols *, $ and -.
     * 
     * @return A unique id string */
    static synchronized String generate() {
      reseedIfNeeded();

      // Generate 20 random bytes (160 bits)
      final byte[] bytes = new byte[20];
      random.nextBytes(bytes);

      // 64 character lookup table (= 2^6, 6 bits)
      final char[] charmap = "1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ*$".toCharArray();

      // First part of the id string is the lookup char of the lower 6
      // bits of each of the random bytes (20 bytes)
      StringBuilder idbuf = new StringBuilder();
      for (byte b : bytes) {
        idbuf.append(charmap[b & 0x3F]);
      }

      // Second part of the id string is the 64 bit timestamp converted
      // into as many 6 bit lookup chars as needed (variable length)
      long time = System.currentTimeMillis();
      long remainder = time;
      while (remainder > 0) {
        idbuf.append(charmap[(int) remainder & 0x3F]);
        remainder = remainder >>> 6;
      }

      // If we have generated other ids during the same millisecond (same
      // millisecond could mean an up to 50 msec interval on some platforms
      // due to a coarse timer resolution) then ensure that we have no
      // collisions ...
      if (time == lastGenTime) {
        // Add a third delimited section (delimiter needed to avoid
        // collisions due to sections two and three being of variable
        // length) with an incremented number mapped to lookup chars
        idbuf.append('-'); // delimiter
        remainder = countSinceTimeChange;
        while (remainder > 0) {
          idbuf.append(charmap[(int) remainder & 0x3F]);
          remainder = remainder >>> 6;
        }
      }
      // ... otherwise reset to prepare the new millisecond
      else {
        countSinceTimeChange = 0;
      }

      countSinceSeed++;
      countSinceTimeChange++;
      lastGenTime = time;
      return idbuf.toString();
    }

    /** Trigger reseed at desired intervals. */
    static protected void reseedIfNeeded() {
      boolean needReseed = false;

      // Reseed if more than 15 minutes have passed since last reseed
      long time = System.currentTimeMillis();
      if (time - seedTime > 15 * 60 * 1000) {
        needReseed = true;
      }

      // Reseed if more than 1000 ids have been generated
      if (countSinceSeed > 1000) {
        needReseed = true;
      }

      if (needReseed) {
        reseed();
        seedTime = time;
        countSinceSeed = 0;
      }
    }

    /** Set up entropy in random number generator */
    // We would really like to reseed using:
    // random.setSeed(random.generateSeed(20));
    // to get 160 bits (SHA1 width) truly random data, but as most
    // Linuxes don't come configured with the driver for the Intel
    // hardware RNG, this usually blocks the whole server...

    protected static void reseed() {
      random.setSeed(System.nanoTime());
    }

   
  
  
  
  
}
