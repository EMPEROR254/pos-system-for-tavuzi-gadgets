package ke.bscodes.possystemfortavuzigadgetsbackend.security;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashing with PBKDF2-HMAC-SHA256 from the JDK (no extra libraries). Stored format:
 * "pbkdf2$iterations$saltBase64$hashBase64". If the project adds Spring Security, its
 * BCryptPasswordEncoder is an equally good choice. Shared module from BS Codes.
 */
public final class PasswordHasher {

  private static final int ITERATIONS = 210_000;
  private static final int KEY_BITS = 256;
  private static final SecureRandom RANDOM = new SecureRandom();

  private PasswordHasher() {}

  public static String hash(String password) {
    byte[] salt = new byte[16];
    RANDOM.nextBytes(salt);
    byte[] hash = pbkdf2(password, salt, ITERATIONS);
    Base64.Encoder b64 = Base64.getEncoder();
    return "pbkdf2$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(hash);
  }

  public static boolean verify(String password, String stored) {
    String[] parts = stored.split("\\$");
    if (parts.length != 4 || !parts[0].equals("pbkdf2")) {
      return false;
    }
    byte[] salt = Base64.getDecoder().decode(parts[2]);
    byte[] expected = Base64.getDecoder().decode(parts[3]);
    byte[] actual = pbkdf2(password, salt, Integer.parseInt(parts[1]));
    // Constant-time comparison.
    return MessageDigest.isEqual(expected, actual);
  }

  private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
    try {
      PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS);
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("PBKDF2 is not available", e);
    }
  }
}
