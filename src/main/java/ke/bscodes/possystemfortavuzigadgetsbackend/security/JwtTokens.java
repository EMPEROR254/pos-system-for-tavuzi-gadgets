package ke.bscodes.possystemfortavuzigadgetsbackend.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signed access tokens (JWT, HS256) with the JDK and Jackson - small enough to
 * read. Keep access tokens short-lived (15 min). Shared module from BS Codes.
 */
public final class JwtTokens {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
  private final byte[] secret;

  public JwtTokens(String secret) {
    if (secret == null || secret.length() < 32) {
      throw new IllegalArgumentException("JWT_SECRET must be at least 32 characters");
    }
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
  }

  public record Claims(String subject, Role role, Instant expiresAt) {}

  public String issue(String subject, Role role, Duration ttl, Instant now) {
    try {
      String header = encode(Map.of("alg", "HS256", "typ", "JWT"));
      String payload =
          encode(Map.of("sub", subject, "role", role.name(), "exp", now.plus(ttl).getEpochSecond()));
      return header + "." + payload + "." + sign(header + "." + payload);
    } catch (Exception e) {
      throw new IllegalStateException("Could not create token", e);
    }
  }

  public Claims verify(String token, Instant now) {
    String[] parts = token.split("\\.");
    if (parts.length != 3) {
      throw new InvalidTokenException("malformed");
    }
    byte[] expected = sign(parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8);
    if (!MessageDigest.isEqual(expected, parts[2].getBytes(StandardCharsets.UTF_8))) {
      throw new InvalidTokenException("bad signature");
    }
    try {
      JsonNode header = JSON.readTree(DECODER.decode(parts[0]));
      if (!"HS256".equals(header.path("alg").asText())) {
        throw new InvalidTokenException("unexpected algorithm");
      }
      JsonNode payload = JSON.readTree(DECODER.decode(parts[1]));
      Instant expiresAt = Instant.ofEpochSecond(payload.path("exp").asLong());
      if (!expiresAt.isAfter(now)) {
        throw new InvalidTokenException("expired");
      }
      return new Claims(payload.path("sub").asText(), Role.valueOf(payload.path("role").asText()), expiresAt);
    } catch (InvalidTokenException e) {
      throw e;
    } catch (Exception e) {
      throw new InvalidTokenException("unreadable");
    }
  }

  private String encode(Map<String, Object> value) throws Exception {
    return ENCODER.encodeToString(JSON.writeValueAsBytes(value));
  }

  private String sign(String input) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return ENCODER.encodeToString(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("HmacSHA256 is not available", e);
    }
  }

  /** The token can't be trusted (map it to HTTP 401). */
  public static class InvalidTokenException extends RuntimeException {
    public InvalidTokenException(String reason) {
      super("Invalid token: " + reason);
    }
  }
}
