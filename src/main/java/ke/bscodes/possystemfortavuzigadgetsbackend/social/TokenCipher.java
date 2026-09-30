package ke.bscodes.possystemfortavuzigadgetsbackend.social;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Encrypts the linked accounts' access tokens before they go into the database (AES-256-GCM, a new
 * random IV each time), so a copied database backup can't be used to post as the shop. The key is
 * derived from SOCIAL_TOKEN_KEY, which lives only in .env.
 */
@Component
public class TokenCipher {

  static final int MIN_KEY_LENGTH = 16;
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  public TokenCipher(SocialProperties props) {
    String secret = props.tokenKey();
    this.key =
        secret == null || secret.length() < MIN_KEY_LENGTH
            ? null
            : new SecretKeySpec(sha256(secret), "AES");
  }

  public boolean ready() {
    return key != null;
  }

  public String encrypt(String plain) {
    if (plain == null) {
      return null;
    }
    try {
      byte[] iv = new byte[IV_BYTES];
      random.nextBytes(iv);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, iv));
      byte[] sealed = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
      byte[] out = new byte[iv.length + sealed.length];
      System.arraycopy(iv, 0, out, 0, iv.length);
      System.arraycopy(sealed, 0, out, iv.length, sealed.length);
      return Base64.getEncoder().encodeToString(out);
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Could not encrypt the token", e);
    }
  }

  /** Throws if the text was changed or encrypted with another key. */
  public String decrypt(String encrypted) {
    if (encrypted == null) {
      return null;
    }
    try {
      byte[] in = Base64.getDecoder().decode(encrypted);
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, in, 0, IV_BYTES));
      return new String(cipher.doFinal(in, IV_BYTES, in.length - IV_BYTES), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      throw new IllegalStateException("The saved token can't be read - link the account again", e);
    }
  }

  private SecretKeySpec requireKey() {
    if (key == null) {
      throw new IllegalStateException("SOCIAL_TOKEN_KEY is not set");
    }
    return key;
  }

  private static byte[] sha256(String s) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
