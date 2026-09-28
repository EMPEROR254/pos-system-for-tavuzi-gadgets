package ke.bscodes.possystemfortavuzigadgetsbackend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Login settings from application.yml under "app.auth" (the secret comes from JWT_SECRET). */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
    String jwtSecret,
    int accessTokenMinutes,
    int refreshTokenDays,
    boolean cookieSecure,
    int maxFailedLogins,
    int lockMinutes) {}
